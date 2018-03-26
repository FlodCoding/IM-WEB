package net.qiujuer.web.italker.push.service;

import com.google.common.base.Strings;
import net.qiujuer.web.italker.push.bean.api.base.ResponseModel;
import net.qiujuer.web.italker.push.bean.api.group.GroupCreateModel;
import net.qiujuer.web.italker.push.bean.api.group.GroupMemberAddModel;
import net.qiujuer.web.italker.push.bean.api.group.GroupMemberUpdateModel;
import net.qiujuer.web.italker.push.bean.card.ApplyCard;
import net.qiujuer.web.italker.push.bean.card.GroupCard;
import net.qiujuer.web.italker.push.bean.card.GroupMemberCard;
import net.qiujuer.web.italker.push.bean.db.Group;
import net.qiujuer.web.italker.push.bean.db.GroupMember;
import net.qiujuer.web.italker.push.bean.db.User;
import net.qiujuer.web.italker.push.factory.GroupFactory;
import net.qiujuer.web.italker.push.factory.PushFactory;
import net.qiujuer.web.italker.push.factory.UserFactory;
import net.qiujuer.web.italker.push.provider.LocalDateTimeConverter;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Path("/group")
public class GroupService extends BaseService {
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ResponseModel<GroupCard> create(GroupCreateModel model) {
        if (!GroupCreateModel.check(model))
            return ResponseModel.buildParameterError();
        User creator = getSelf();
        //TODO Set的remove是根据什么
        model.getUsers().remove(creator.getId());
        if (model.getUsers().size() == 0)
            return ResponseModel.buildParameterError();
        //检查是否已有该群
        if (GroupFactory.findByName(model.getName()) != null) {
            return ResponseModel.buildHaveNameError();
        }

        List<User> users = new ArrayList<>();
        for (String u : model.getUsers()) {
            User user = UserFactory.findById(u);
            if (user == null)
                continue;
            users.add(user);
        }
        if (users.size() == 0) {
            return ResponseModel.buildHaveNameError();
        }
        //数据库创建群
        Group group = GroupFactory.create(creator, model, users);
        if (group == null)
            return ResponseModel.buildServiceError();

        //拿到管理员（自己）  User creator = getSelf();？？
        GroupMember creatorMember = GroupFactory.getMember(creator.getId(), group.getId());
        if (creatorMember == null)
            return ResponseModel.buildServiceError();

        Set<GroupMember> members = GroupFactory.getMembers(group);
        if (members == null)
            return ResponseModel.buildServiceError();

        members = members.stream()
                .filter(groupMember -> {
                    return !groupMember.getId().equalsIgnoreCase(creatorMember.getId());
                }) //如果这些成员跟管理员的Id不匹配都collect上
                .collect(Collectors.toSet());
        //给群成员发送已经添加到群的消息
        PushFactory.pushNewMemberAddOK(members);

        return ResponseModel.buildOk(new GroupCard(creatorMember));
    }

    /**
     * 搜索出来的群列表
     *
     * @param name 群的名字
     * @return 包裹GroupCard的数据
     */
    @GET
    @Path("/search/{name:(.*)?}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ResponseModel<List<GroupCard>> search(@PathParam("name") @DefaultValue("") String name) {
        User user = getSelf();
        List<Group> groups = GroupFactory.search(name);
        if (groups != null && groups.size() > 0) {
            List<GroupCard> groupCards = groups.stream()
                    .map(group -> {
                        GroupMember member = GroupFactory.getMember(user.getId(), group.getId());
                        return new GroupCard(group, member);
                    }).collect(Collectors.toList());
            return ResponseModel.buildOk(groupCards);
        }
        return ResponseModel.buildOk();
    }

    /**
     * 拉取最近群列表
     *
     * @param dateString
     * @return
     */
    @GET
    @Path("/list/{date:(.*)?}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ResponseModel<List<GroupCard>> list(@PathParam("date") @DefaultValue("") String dateString) {
        User self = getSelf();
        LocalDateTime dateTime = null;
        //解析传入的时间
        if (!Strings.isNullOrEmpty(dateString)) {
            try {
                dateTime = LocalDateTime.parse(dateString, LocalDateTimeConverter.FORMATTER);
            } catch (Exception e) {
                dateTime = null;
            }
        }

        Set<GroupMember> members = GroupFactory.getMembers(self);
        if (members == null || members.size() == 0)
            return ResponseModel.buildOk();

        final LocalDateTime finalDateTime = dateTime;
        List<GroupCard> groupCards = members.stream()
                .filter(groupMember -> finalDateTime == null
                        || groupMember.getUpdateAt().isAfter(finalDateTime))
                .map(GroupCard::new) //每个menber放到GroupCard中new
                .collect(Collectors.toList());

        return ResponseModel.buildOk(groupCards);
    }

    /**
     * 获取当前群的信息
     * 要求请求者是群中的一员
     *
     * @param groupId 群Id
     * @return 群信息
     */
    @GET
    @Path("{groupId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ResponseModel<GroupCard> getGroup(@PathParam("groupId") String groupId) {
        if (Strings.isNullOrEmpty(groupId))
            return ResponseModel.buildParameterError();

        User self = getSelf();
        GroupMember member = GroupFactory.getMember(self.getId(), groupId);
        if (member == null)
            return ResponseModel.buildNotFoundGroupError(null);
        return ResponseModel.buildOk(new GroupCard(member));

    }

    /**
     * 获取当前群的所有成员，
     * 请求者必须是群成员之一
     *
     * @param groupId 群Id
     * @return 群成员列表
     */
    @GET
    @Path("/{groupId}/member")  // id/member 多一个member标识为要取成员
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ResponseModel<List<GroupMemberCard>> getGroupMembers(@PathParam("groupId") String groupId) {
        User self = getSelf();
        Group group = GroupFactory.findById(groupId);
        if (group == null)
            return ResponseModel.buildNotFoundGroupError(null);

        GroupMember selfMember = GroupFactory.getMember(self.getId(), groupId);
        if (selfMember == null)
            return ResponseModel.buildNoPermissionError();

        Set<GroupMember> members = GroupFactory.getMembers(group);
        if (members == null)
            return ResponseModel.buildServiceError();
        List<GroupMemberCard> memberCards = members.stream()
                .map(GroupMemberCard::new) //相当于把members中的每一个成员丢到GroupMemberCard的new方法中
                .collect(Collectors.toList());
        //.map(groupMember -> { return new GroupMemberCard(groupMember);})
        return ResponseModel.buildOk(memberCards);
    }

    /**
     * 给群添加群成员，
     * 请求者必须群管理员
     *
     * @param groupId 群Id
     * @param model   添加群成员Model
     * @return 添加的群成员列表
     */
    @POST
    @Path("/{groupId}/member")  // id/member 多一个member标识为要取成员
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ResponseModel<List<GroupMemberCard>> GroupMembersAdd(@PathParam("groupId") String groupId, GroupMemberAddModel model) {
        if (Strings.isNullOrEmpty(groupId) || !GroupMemberAddModel.check(model))
            return ResponseModel.buildParameterError();
        User self = getSelf();
        model.getUsers().remove(self.getId()); //移除掉自己
        if (model.getUsers().size() == 0) //TODO 只有自己一人的群有何不可
            return ResponseModel.buildParameterError();
        Group group = GroupFactory.findById(groupId);
        if (group == null)
            return ResponseModel.buildNotFoundGroupError(null);
        GroupMember selfMember = GroupFactory.getMember(self.getId(), groupId);
        if (selfMember == null || selfMember.getPermissionType() == GroupMember.PERMISSION_TYPE_NONE)
            return ResponseModel.buildNoPermissionError();

        //现有的群成员
        Set<GroupMember> oldMembers = GroupFactory.getMembers(group);
        Set<String> oldMembersId = oldMembers.stream()
                .map(GroupMember::getUserId)
                .collect(Collectors.toSet());

        //需要添加的群成员
        List<User> newUsers = new ArrayList<>();
        for (String u : model.getUsers()) {
            User newMember = UserFactory.findById(u);
            if (newMember == null || oldMembersId.contains(newMember.getId()))
                continue; //如果新的成员已经在旧的成员里就跳过
            newUsers.add(newMember);
        }
        //如果新的成员列表个数是0，就返回错误
        if (newUsers.size()==0)
            return ResponseModel.buildParameterError();

        //添加到数据库中，返回GroupMember Set
        Set<GroupMember> newMembers = GroupFactory.addMembers(group,newUsers);
        if (newMembers==null)
            return ResponseModel.buildServiceError();
        //将这些数据Set转换成List
        List<GroupMemberCard> newMemberCards = newMembers.stream()
                .map(GroupMemberCard::new)
                .collect(Collectors.toList());

        //1、通知每个新成员，加入了xxx群
        //2、通知每个老成员，有xxx加入了群
        PushFactory.pushNewMemberAddOK(newMembers);
        PushFactory.pushOldMemberAddOK(oldMembers,newMemberCards);

        return ResponseModel.buildOk(newMemberCards);

    }


    /**
     * 更改群成员某成员信息，请求者是管理员或者是自己
     *
     * @param memberId 成员Id，可以查询到对应的群和人
     * @param model    修改的model
     * @return 当前的成员信息
     */
    @PUT
    @Path("/member/{memberId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ResponseModel<GroupMemberCard> modifyMember(@PathParam("memberId") String memberId, GroupMemberUpdateModel model) {

        return null;
    }

    /**
     * 申请加入群
     * 返回一个加入申请卡 -> 给管理员发推送 ——> 管理员同意 -> 调用GroupMembersAdd接口
     *
     * @param groupId 群Id
     * @return 申请表
     */
    @POST
    @Path("/applyJoin/{groupId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ResponseModel<ApplyCard> joinGroup(@PathParam("groupId") String groupId) {
        return null;
    }

}
