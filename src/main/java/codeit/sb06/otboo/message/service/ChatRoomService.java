package codeit.sb06.otboo.message.service;

import codeit.sb06.otboo.message.entity.ChatRoom;
import codeit.sb06.otboo.user.entity.User;

public interface ChatRoomService {

    ChatRoom getOrCreatePrivateRoom(User sender, User receiver);
}
