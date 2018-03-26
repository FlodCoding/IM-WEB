package net.qiujuer.web.italker.push.factory;

import com.google.common.base.Strings;
import net.qiujuer.web.italker.push.bean.api.base.PushModel;
import net.qiujuer.web.italker.push.bean.card.GroupMemberCard;
import net.qiujuer.web.italker.push.bean.card.MessageCard;
import net.qiujuer.web.italker.push.bean.card.UserCard;
import net.qiujuer.web.italker.push.bean.db.*;
import net.qiujuer.web.italker.push.utils.Hib;
import net.qiujuer.web.italker.push.utils.PushDispatcher;
import net.qiujuer.web.italker.push.utils.TextUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

//消息存储的处理的工具类
public class PushFactory {
    //发送一条消息，在消息历史中记录
    public static void pushNewMessage(User sender, Message message) {
        if (sender == null || message == null)
            return;

        // 消息卡片用于发送
        MessageCard card = new MessageCard(message);
        // 要推送的字符串
        String entity = TextUtil.toJson(card);

        // 发送者
        PushDispatcher dispatcher = new PushDispatcher();

        if (message.getGroup() == null
                && Strings.isNullOrEmpty(message.getGroupId())) {
            // 是单聊的消息

            User receiver = UserFactory.findById(message.getReceiverId());
            if (receiver == null)
                return;

            // 历史记录表字段建立
            PushHistory history = new PushHistory();
            // 普通消息类型
            //TODO 可以改成build
            history.setEntityType(PushModel.ENTITY_TYPE_MESSAGE);
            history.setEntity(entity);
            history.setReceiver(receiver);
            // 接收者当前的设备推送Id
            history.setReceiverPushId(receiver.getPushId());


            // 推送的真实Model
            PushModel pushModel = new PushModel();

            // 使用历史记录来建立model
            pushModel.add(history.getEntityType(), history.getEntity());

            // 把需要发送的数据，丢给发送者进行发送
            dispatcher.add(receiver, pushModel);

            // 保存到数据库
            Hib.queryOnly(session -> session.save(history));
        } else {

            Group group = message.getGroup();
            // 因为延迟加载情况可能为null，需要通过Id查询
            if (group == null)
                group = GroupFactory.findById(message.getGroupId());

            // 如果群真的没有，则返回
            if (group == null)
                return;

            // 给群成员发送消息
            //找到要发送的所有的人
            Set<GroupMember> members = GroupFactory.getMembers(group);
            if (members == null || members.size() == 0)
                return;

            // 过滤我自己
            members = members.stream()
                    .filter(groupMember -> !groupMember.getUserId()
                            .equalsIgnoreCase(sender.getId())) //只要groupMember不是sender都满足
                    .collect(Collectors.toSet());
            if (members.size() == 0)
                return;

            // 一个历史记录列表
            List<PushHistory> histories = new ArrayList<>();

            addGroupMembersPushModel(dispatcher, // 推送的发送者
                    histories, // 数据库要存储的列表
                    members,    // 所有的成员
                    entity, // 要发送的数据
                    PushModel.ENTITY_TYPE_MESSAGE); // 发送的类型

            // 保存到数据库的操作
            Hib.queryOnly(session -> {
                for (PushHistory history : histories) {
                    session.saveOrUpdate(history);
                }
            });
        }


        // 发送者进行真实的提交
        dispatcher.submit();

    }

    /**
     * 给群成员构建一个消息，
     * 把消息存储到数据库的历史记录中，每个人，每条消息都是一个记录
     */
    private static void addGroupMembersPushModel(PushDispatcher dispatcher,
                                                 List<PushHistory> histories,
                                                 Set<GroupMember> members,
                                                 String entity,
                                                 int entityTypeMessage) {
        for (GroupMember member : members) {
            // 无须通过Id再去找用户
            User receiver = member.getUser();
            if (receiver == null)
                return;

            // 历史记录表字段建立
            PushHistory history = new PushHistory();
            history.setEntityType(entityTypeMessage);
            history.setEntity(entity);
            history.setReceiver(receiver);
            history.setReceiverPushId(receiver.getPushId());
            histories.add(history);

            // 构建一个消息Model
            PushModel pushModel = new PushModel();
            pushModel.add(history.getEntityType(), history.getEntity());

            // 添加到发送者的数据集中
            dispatcher.add(receiver, pushModel);
        }
    }


    //给新成员发送已经添加到群的消息
    public static void pushNewMemberAddOK(Set<GroupMember> members) {
        //发送者对象
        PushDispatcher dispatcher = new PushDispatcher();
        //历史记录表
        List<PushHistory> histories = new ArrayList<>();
        for (GroupMember member : members) {
            User receiver = member.getUser();
            if (receiver == null)
                return;

            //成员卡片
            GroupMemberCard memberCard = new GroupMemberCard(member);
            //json实体
            String entity = TextUtil.toJson(memberCard);
            //历史记录字段表
            PushHistory history = new PushHistory();
            //设置实体类型：添加到群
            history.setEntityType(PushModel.ENTITY_TYPE_ADD_GROUP);
            history.setEntity(entity);
            history.setReceiver(receiver);
            history.setReceiverPushId(receiver.getPushId());
            histories.add(history);

            // 构建一个消息Model
            PushModel pushModel = new PushModel();
            pushModel.add(history.getEntityType(), history.getEntity());

            // 添加到发送者的数据集中
            dispatcher.add(receiver, pushModel);

            //histories.add(history);
        }
        // 保存到数据库的操作
        Hib.queryOnly(session -> {
            for (PushHistory history : histories) {
                session.saveOrUpdate(history);
            }
        });
        dispatcher.submit();

    }

    /**
     * 通知旧成员有新的成员加入
     * @param oldMembers 老成员
     * @param newMemberCards 新成员卡片
     */
    public static void pushOldMemberAddOK(Set<GroupMember> oldMembers, List<GroupMemberCard> newMemberCards) {
        //发送者对象
        PushDispatcher dispatcher = new PushDispatcher();
        //历史记录表
        List<PushHistory> histories = new ArrayList<>();

        String entity = TextUtil.toJson(newMemberCards);

        //给每个旧成员添加消息Model和历史消息，消息内容是newMemberCards的
        addGroupMembersPushModel(dispatcher,histories,oldMembers,entity,PushModel.ENTITY_TYPE_ADD_GROUP_MEMBERS);
        Hib.queryOnly(session -> {
            for (PushHistory history : histories) {
                session.saveOrUpdate(history);
            }
        });
        dispatcher.submit();
    }

    /**
     * 推送退出消息
     * @param receiver 推送接收者
     * @param pushId 当前接收者的pushId
     */
    public static void pushLogout(User receiver, String pushId) {
        PushHistory history = new PushHistory();
        history.setEntityType(PushModel.ENTITY_TYPE_LOGOUT);
        history.setEntity("Account Logout!");
        history.setReceiver(receiver);
        history.setReceiverPushId(receiver.getPushId());
        //保存历史记录表到数据库
        Hib.queryOnly(session -> session.save(history));

        PushDispatcher dispatcher = new PushDispatcher();
        PushModel pushModel = new PushModel()
                .add(history.getEntityType(),history.getEntity());

        //添加和提交
        dispatcher.add(receiver,pushModel);
        dispatcher.submit();
    }

    /**
     * 给被关注的人发送关注消息
     * @param receiver 被关注者
     * @param userCard 关注者卡片
     */
    public static void pushFollow(User receiver, UserCard userCard) {
        userCard.setIsFollow(true);
        String entity = TextUtil.toJson(userCard);
        PushHistory history = new PushHistory();
        history.setEntityType(PushModel.ENTITY_TYPE_ADD_FRIEND);
        history.setEntity(entity);
        history.setReceiver(receiver);
        history.setReceiverPushId(receiver.getPushId());

        //保存历史记录表到数据库
        Hib.queryOnly(session -> session.save(history));

        PushDispatcher dispatcher = new PushDispatcher();
        PushModel pushModel = new PushModel()
                .add(history.getEntityType(),history.getEntity());

        //添加和提交
        dispatcher.add(receiver,pushModel);
        dispatcher.submit();
    }
}
