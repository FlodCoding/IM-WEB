package net.qiujuer.web.italker.push.factory;

import com.google.common.base.Strings;
import net.qiujuer.web.italker.push.bean.api.group.GroupCreateModel;
import net.qiujuer.web.italker.push.bean.db.Group;
import net.qiujuer.web.italker.push.bean.db.GroupMember;
import net.qiujuer.web.italker.push.bean.db.User;
import net.qiujuer.web.italker.push.utils.Hib;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class GroupFactory {
    public static Group findById(String groupId) {
        return Hib.query(session -> session.get(Group.class, groupId));
    }

    // 查询一个群, 同时该User必须为群的成员，否则返回null
    public static Group findById(User user, String groupId) {
        GroupMember member = getMember(user.getId(), groupId);
        if (member != null)
            return member.getGroup();

        return null;
    }

    //拿到群的所有成员
    public static Set<GroupMember> getMembers(Group group) {
        return Hib.query(session -> {
            @SuppressWarnings("unchecked")
            List<GroupMember> members = session.createQuery("from GroupMember where group=:group")
                    .setParameter("group", group)
                    .list();
            return new HashSet<>(members);
        });
    }

    //用户加入的所有群
    public static Set<GroupMember> getMembers(User user) {
        return Hib.query(session -> {
            @SuppressWarnings("unchecked")
            List<GroupMember> members = session.createQuery("from GroupMember where userId=:userId")
                    .setParameter("userId", user.getId())
                    .list();
            return new HashSet<>(members);
        });
    }

    public static Group findByName(String name) {
        return Hib.query(session -> (Group) session
                .createQuery("from Group where lower(name)=:name")
                .setParameter("name", name.toLowerCase())
                .uniqueResult());
    }

    public static Group create(User creator, GroupCreateModel model, List<User> users) {
        return Hib.query(session -> {
            Group group = new Group(creator, model);
            session.save(group);
            GroupMember ownerMember = new GroupMember(creator, group);
            //设置权限为群主
            ownerMember.setPermissionType(GroupMember.PERMISSION_TYPE_ADMIN_SU);
            session.save(ownerMember);

            for (User user : users) {
                GroupMember member = new GroupMember(user, group);
                session.save(member);
            }
            //session.flush();
            //session.load(group,group.getId());
            return group;
        });
    }

    //找群当中的一个人
    public static GroupMember getMember(String userId, String groupId) {

        return Hib.query(session -> (GroupMember) session
                .createQuery("from GroupMember where userId=:userId and groupId=:groupId")
                .setParameter("userId", userId)
                .setParameter("groupId", groupId)
                .setMaxResults(1)
                .uniqueResult()
        );
    }

    @SuppressWarnings("unchecked")
    public static List<Group> search(String name) {
        if (Strings.isNullOrEmpty(name))
            name = ""; //保证不是null的情况，减少额外错误
        final String searchName = "%" + name + "%"; // 模糊匹配
        return Hib.query(session -> {
            //查询条件：name忽略大小写并使用模糊查询，必须有描述和头像才能查到
            return (List<Group>) session.createQuery("from Group where lower(name) like :name")
                    .setParameter("name", searchName)
                    .setMaxResults(20) // 至多20条
                    .list();
        });
    }

    public static Set<GroupMember> addMembers(Group group, List<User> newMembers) {
        return Hib.query(session -> {
            Set<GroupMember> members = new HashSet<>();
            for (User user : newMembers) {
                GroupMember member = new GroupMember(user, group);
                session.save(member);
                members.add(member);
            }
            return members;
        });

    }
}

