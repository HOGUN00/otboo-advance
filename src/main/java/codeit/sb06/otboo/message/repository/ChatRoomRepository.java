package codeit.sb06.otboo.message.repository;

import codeit.sb06.otboo.message.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, UUID> {

    Optional<ChatRoom> findByDmKey(String dmKey);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO chat_rooms (id, dm_key)
            VALUES (:id, :dmKey)
            ON CONFLICT (dm_key) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("dmKey") String dmKey);
}
