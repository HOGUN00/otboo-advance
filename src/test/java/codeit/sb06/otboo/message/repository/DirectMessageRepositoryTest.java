package codeit.sb06.otboo.message.repository;

import codeit.sb06.otboo.config.JpaAuditingConfig;
import codeit.sb06.otboo.config.QueryDslConfig;
import codeit.sb06.otboo.message.entity.ChatRoom;
import codeit.sb06.otboo.message.entity.DirectMessage;
import codeit.sb06.otboo.user.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@DataJpaTest
@Import({JpaAuditingConfig.class, QueryDslConfig.class})
class DirectMessageRepositoryTest {

    @Autowired
    private DirectMessageRepository directMessageRepository;

    @Autowired
    private EntityManager em;

    private ChatRoom chatRoom;


    @BeforeEach
    void setUp() {
        User sender = new User();
        em.persist(sender);

        String dmKey = "uuid_test_dm_key";
        chatRoom = new ChatRoom(UUID.randomUUID(), dmKey, new HashSet<>());
        em.persist(chatRoom);

        for (int i = 1; i <= 20; i++) {
            DirectMessage directMessage = DirectMessage.builder()
                    .sender(sender)
                    .chatRoom(chatRoom)
                    .content("안녕 " + i)
                    .build();
            em.persist(directMessage);
            em.flush();
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        em.clear();
    }

    @Test
    @DisplayName("커서 페이지네이션 쿼리 테스트")
    void testCursorPaginationQuery() {
        // given
        Pageable pageable = PageRequest.of(0, 10);

        // when
        Slice<DirectMessage> firstPage = directMessageRepository.findFirstPageByChatRoom(chatRoom, pageable);
        List<DirectMessage> firstPageContent = firstPage.getContent();

        // then
        assertAll(
                () -> assertThat(firstPage).hasSize(10),
                // 최신 메시지부터 조회되는지 확인
                () -> assertThat(firstPageContent.get(0).getContent()).isEqualTo("안녕 20"),
                () -> assertThat(firstPageContent.get(9).getContent()).isEqualTo("안녕 11")
        );
    }
}
