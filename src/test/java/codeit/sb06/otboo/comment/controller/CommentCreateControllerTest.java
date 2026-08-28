package codeit.sb06.otboo.comment.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static codeit.sb06.otboo.security.SecurityTestFixtures.userDto;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import codeit.sb06.otboo.comment.dto.AuthorDto;
import codeit.sb06.otboo.comment.dto.CommentCreateRequest;
import codeit.sb06.otboo.comment.dto.CommentDto;
import codeit.sb06.otboo.comment.service.CommentService;
import codeit.sb06.otboo.exception.comment.CommentCreateFailException;
import codeit.sb06.otboo.security.jwt.JwtAuthenticationFilter;
import codeit.sb06.otboo.security.jwt.JwtTokenProvider;
import codeit.sb06.otboo.security.user.OtbooUserDetails;
import codeit.sb06.otboo.user.dto.UserDto;
import codeit.sb06.otboo.user.entity.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CommentController.class)
@AutoConfigureMockMvc(addFilters = false)
public class CommentCreateControllerTest {

  @Autowired
  MockMvc mockMvc;

  @MockitoBean
  RoleHierarchy roleHierarchy;

  @MockitoBean
  JwtTokenProvider jwtTokenProvider;

  @MockitoBean
  JwtAuthenticationFilter jwtAuthenticationFilter;


  @MockitoBean
  CommentService commentService;

  @Autowired
  ObjectMapper objectMapper;


  private final UUID feedId = UUID.randomUUID();
  private final UUID authorId = UUID.randomUUID();

  @BeforeEach
  void setUpAuthentication() {
    OtbooUserDetails principal = new OtbooUserDetails(
        userDto(authorId, Role.USER), "password", Map.of());
    SecurityContextHolder.getContext().setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(
            principal, principal.getPassword(), principal.getAuthorities()));
  }

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void createComment_success_200response() throws Exception {

    // given
    CommentDto response = new CommentDto(
        UUID.randomUUID(),
        LocalDateTime.now(),
        feedId,
        new AuthorDto(authorId, "테스트", null),
        "테스트용 댓글"


    );

    when(commentService.createComment(eq(authorId), eq(feedId), any()))
        .thenReturn(response);

    // when
    CommentCreateRequest commentCreateRequest = new CommentCreateRequest(feedId,authorId,"테스트용 댓글");


    // then
    mockMvc.perform(
            post("/api/feeds/{feedId}/comments", feedId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(commentCreateRequest))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.createdAt").exists())
        .andExpect(jsonPath("$.feedId").value(feedId.toString()))
        .andExpect(jsonPath("$.author.userId").value(authorId.toString()))
        .andExpect(jsonPath("$.author.name").value("테스트"))
        .andExpect(jsonPath("$.content").value("테스트용 댓글"));

  }

  @Test
  void createComment_fail_400response() throws Exception {

    // given
    when(commentService.createComment(eq(authorId), eq(feedId), any()))
        .thenThrow(new CommentCreateFailException());

    CommentCreateRequest request =
        new CommentCreateRequest(feedId, authorId, ""); // 실패 유도

    // when & then
    mockMvc.perform(
            post("/api/feeds/{feedId}/comments", feedId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.exceptionName")
            .value("CommentCreateFailException"))
        .andExpect(jsonPath("$.message")
            .value("댓글 등록 실패"))
        .andExpect(jsonPath("$.details").exists());
  }

}
