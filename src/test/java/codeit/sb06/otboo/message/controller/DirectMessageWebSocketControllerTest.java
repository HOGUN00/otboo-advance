package codeit.sb06.otboo.message.controller;

import codeit.sb06.otboo.config.JpaAuditingConfig;
import codeit.sb06.otboo.message.dto.DirectMessageDto;
import codeit.sb06.otboo.message.dto.request.DirectMessageCreateRequest;
import codeit.sb06.otboo.message.entity.ChatRoom;
import codeit.sb06.otboo.message.repository.ChatMemberRepository;
import codeit.sb06.otboo.message.repository.ChatRoomRepository;
import codeit.sb06.otboo.message.repository.DirectMessageRepository;
import codeit.sb06.otboo.notification.config.EmbeddedRedisInitializer;
import codeit.sb06.otboo.notification.repository.NotificationRepository;
import codeit.sb06.otboo.security.jwt.JwtRegistry;
import codeit.sb06.otboo.security.jwt.JwtTokenProvider;
import codeit.sb06.otboo.user.entity.User;
import codeit.sb06.otboo.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@Slf4j
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.data.redis.port=16379"
)
@Import(JpaAuditingConfig.class)
@ActiveProfiles("test")
@ContextConfiguration(initializers = EmbeddedRedisInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DirectMessageWebSocketControllerTest {

    @LocalServerPort
    private int port;

    private WebSocketStompClient stompClient;
    private StompSession session;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private ChatMemberRepository chatMemberRepository;

    @Autowired
    private DirectMessageRepository directMessageRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private JwtRegistry jwtRegistry;

    @BeforeEach
    void setup() {
        // 클라이언트 설정 (JSON 변환기 포함)
        this.stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        this.stompClient.setMessageConverter(new CompositeMessageConverter(List.of(
                new StringMessageConverter(),
                new MappingJackson2MessageConverter()
        )));
        given(jwtTokenProvider.validateAccessToken(anyString())).willReturn(true);
        given(jwtRegistry.hasActiveJwtInformationByAccessToken(anyString())).willReturn(true);
    }

    @AfterEach
    void cleanUp() {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
        stompClient.stop();

        directMessageRepository.deleteAllInBatch();
        chatMemberRepository.deleteAllInBatch();
        chatRoomRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        notificationRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("메시지를 보내면 해당 DM 방 구독자에게 메시지가 전달된다.")
    void sendDirectMessageTest() throws Exception {

        // 0. 테스트용 유저 생성
        User sender = userRepository.save(new User());
        User receiver = userRepository.save(new User());
        given(jwtTokenProvider.getUserId(anyString())).willReturn(sender.getId());

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer test-access-token");

        // 1. 연결 세션 확보
        String url = "ws://localhost:" + port + "/ws/websocket"; // 서버의 WebSocket 엔드포인트 + with SockJS
        session = stompClient.connectAsync(
                        url, (WebSocketHttpHeaders) null, connectHeaders, new StompSessionHandlerAdapter() {
                        })
                .get(3, TimeUnit.SECONDS);

        // 2. 메시지를 받을 큐(Queue) 준비
        BlockingQueue<String> resultQueue = new LinkedBlockingDeque<>();

        // 3. 특정 DM 방 구독 (예: sender, receiver 간의 DM 방)
        String dmKey = ChatRoom.generateDmKey(sender.getId(), receiver.getId());
        String destination = "/sub/direct-messages_" + dmKey;
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                log.info("메시지 수신 성공: {}", payload);
                resultQueue.offer((String) payload);
            }
        });

        Thread.sleep(300);

        // 4. 메시지 전송 (컨트롤러의 @MessageMapping으로)
        DirectMessageCreateRequest request = new DirectMessageCreateRequest(receiver.getId(), sender.getId(), "안녕!");
        session.send("/pub/direct-messages_send", request);

        // 5. 검증: 5초 안에 구독 중인 큐에 메시지가 들어오는지 확인
        String receivedJson = resultQueue.poll(5, TimeUnit.SECONDS);
        assertThat(receivedJson).isNotNull();

        DirectMessageDto received = objectMapper.readValue(receivedJson, DirectMessageDto.class);
        assertAll(
                () -> assertThat(received.content()).isEqualTo("안녕!"),
                () -> assertThat(received.sender().userId()).isEqualTo(sender.getId()),
                () -> assertThat(received.receiver().userId()).isEqualTo(receiver.getId())
        );
    }
}
