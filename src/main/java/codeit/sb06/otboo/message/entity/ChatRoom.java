package codeit.sb06.otboo.message.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.*;

@Entity
@Table(name = "chat_rooms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ChatRoom {

    @Id
    // getOrCreatePrivateRoom의 충돌 안전 INSERT에서 UUID를 직접 생성한다.
    private UUID id;

    @Column(name = "dm_key", nullable = false, unique = true)
    private String dmKey;

    @OneToMany(mappedBy = "chatRoom", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ChatMember> chatMembers = new HashSet<>();

    public ChatRoom(String dmKey) {
        this.dmKey = dmKey;
    }

    public static String generateDmKey(UUID senderId, UUID receiverId) {
        List<String> ids = Arrays.asList(senderId.toString(), receiverId.toString());
        Collections.sort(ids);
        return ids.get(0) + "_" + ids.get(1);
    }

    public void addChatMember(ChatMember chatMember) {
        if (chatMembers.add(chatMember)) {
            chatMember.setChatRoom(this);
        }
    }
}
