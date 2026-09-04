package codeit.sb06.otboo.message.service;

import codeit.sb06.otboo.message.entity.ChatMember;
import codeit.sb06.otboo.message.entity.ChatRoom;
import codeit.sb06.otboo.user.entity.User;

public interface ChatMemberService {

    ChatMember create(ChatRoom chatRoom, User user);
}
