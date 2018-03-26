package net.qiujuer.web.italker.push.factory;


import com.google.common.base.Strings;
import net.qiujuer.web.italker.push.bean.db.User;
import net.qiujuer.web.italker.push.bean.db.UserFollow;
import net.qiujuer.web.italker.push.utils.Hib;
import net.qiujuer.web.italker.push.utils.TextUtil;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class UserFactory {
    public static User findByPhone(String phone) {
        return Hib.query(session -> (User) session
                .createQuery("from User where phone=:inPhone")
                //从 User表去 找Phone
                .setParameter("inPhone", phone)
                .uniqueResult());
    }

    public static User findByName(String name) {
        return Hib.query(session -> (User) session
                .createQuery("from User where name=:name")
                .setParameter("name", name)
                .uniqueResult());
    }

    //通过Token字段查询用户信息
    public static User findByToken(String token) {
        return Hib.query(session -> (User) session
                .createQuery("from User where token=:token")
                //从 User表去 找Phone
                .setParameter("token", token)
                .uniqueResult());
    }

    //通过id查询用户信息
    public static User findById(String id) {
        return Hib.query(session -> session.get(User.class, id));//直接查询到Id
    }

    //更新信息到数据库
    public static User update(User user) {
        return Hib.query(session -> {
            session.saveOrUpdate(user);
            return user;
        });
    }


    public static User login(String account, String password) {
        String accountStr = account.trim();
        //注册时有加过密，需要同样的操作才能匹配
        String encodePassword = encodePassword(password);
        //查询数据库
        User user = Hib.query(session -> (User) session
                .createQuery("from User where phone=:phone and password=:password")
                .setParameter("phone", accountStr)
                .setParameter("password", encodePassword)
                .uniqueResult());
        if (user != null) {
            //产生一个Token
            user = login(user);
        }
        return user;
    }

    /**
     * 登陆操作
     *
     * @param user
     * @return
     */
    private static User login(User user) {
        //使用一个随机的UUID充当Token
        String newToken = UUID.randomUUID().toString();
        //Base64格式化
        newToken = TextUtil.encodeBase64(newToken);
        user.setToken(newToken);
        return update(user);
    }

    /**
     * 给当前账户绑定pushID
     *
     * @param user
     * @param pushId
     * @return
     */
    public static User bindPushId(User user, String pushId) {
        if (Strings.isNullOrEmpty(pushId)) {
            return null;
        }
        //查询是否有其他账户绑定这个设备
        //取消绑定，避免推送混乱
        Hib.queryOnly(session -> {  //非当前user的查询，找到他并且把pushId清除掉
            @SuppressWarnings("unchecked")
            List<User> userList = (List<User>) session
                    .createQuery("from User where loswer(pushId)=:pushId and id!=:useId")//忽略大小写
                    .setParameter("pushId", pushId.toLowerCase())
                    .setParameter("useId", user.getId())
                    .list();
            for (User u : userList) {
                u.setPushId(null);
                session.saveOrUpdate(u);
            }
        });

        if (pushId.equalsIgnoreCase(user.getPushId())) {
            //如果当前需要绑定的设备Id在之前已经绑定过了
            //就不需要重现绑定
            return user;
        } else {
            //如果当前账户之前设备id和需要绑定的不同
            //要求之前设备退出账户，给之前设备推送一条推送消息
            if (Strings.isNullOrEmpty(user.getPushId())) {
                PushFactory.pushLogout(user,user.getPushId());
            }

            //更新新的设备Id
            user.setPushId(pushId);
            return update(user);
        }
    }

    /**
     * 用户注册
     * 注册的操作需要写入数据库，并返回数据库中的User信息
     *
     * @param account  账户
     * @param password 密码
     * @param name     用户名
     * @return User
     */
    public static User register(String account, String password, String name) {
        // 去除账户中的首位空格
        account = account.trim();
        // 处理密码
        password = encodePassword(password);

        User user = createUser(account, password, name);

        if (user != null) {
            user = login(user);
        }
        return user;
    }


    //对密码加密
    private static String encodePassword(String password) {
        // 密码去除首尾空格
        password = password.trim();
        // 进行MD5非对称加密，加盐会更安全，盐也需要存储
        password = TextUtil.getMD5(password);
        // 再进行一次对称的Base64加密，可以采取加盐的方案（加上当前时间）
        return TextUtil.encodeBase64(password);
    }

    /**
     * 6 - 2
     * 创建用户的逻辑
     *
     * @param account  手机号
     * @param password 密码
     * @param name     用户名
     * @return 返回一个用户
     */
    private static User createUser(String account, String password, String name) {
        User user = new User();

        user.setName(name);
        user.setPassword(password);
        // 账户就是手机号
        user.setPhone(account);


        //存到数据库中
        //session -> (User) session.save(user)
        //接收一个session -> 它的值是(User) session.save(user)
        return Hib.query(session -> {
            session.save(user);
            return user;
        }); //TODO JAVA8 lambda*/
    }

    /**
     * 获取联系人的列表
     * @param self
     * @return
     */
    public static List<User> contact(User self) {
        //self.getFollowing() 是不能用的，之前虽然有开启一次事务，但是加载完后就被销毁了
        return Hib.query(session -> {
            //因为是懒加载，所以要在load一次
            session.load(self, self.getId());
            Set<UserFollow> flows = self.getFollowing();
            /*List<User> users = new ArrayList<>();  //方法一
            for (UserFollow follow : flows){
                users.add(follow.getTarget());
            }*/
            return flows.stream()
                    .map(UserFollow::getTarget) //遍历和返回当前类的getTarget方法
                    .collect(Collectors.toList());
        });
    }

    /**
     * 关注人的操作
     * @param origin 发起者
     * @param target 被关注人
     * @param alias 备注名
     * @return 被关注人信息
     */
    public static User follow(final User origin,final User target,final String alias){
        UserFollow follow = getUserFollow(origin,target); //是否已经关注
        if (follow!=null){//已经关注了，直接返回
            return follow.getTarget();
        }
        return Hib.query(session -> {
           session.load(origin,origin.getId()); //因为是懒加载的，拉取信息还必须要load
           session.load(target,target.getId());
           //我关注人时，同时他也关注我，要添加两条UserFollow数据
           UserFollow originFollow = new UserFollow();
           originFollow.setOrigin(origin);
           originFollow.setTarget(target);
           //备注是我对他的，他对我是没有备注的
           originFollow.setAlias(alias);

           //他关注我
            UserFollow targetFollow = new UserFollow();
            targetFollow.setOrigin(target);
            targetFollow.setTarget(origin);

            //保存到数据库
            session.save(originFollow);
            session.save(targetFollow);

            return target;
        });
    }

    /**
     * 查询两个人是否被关注
     * @param origin 发起者
     * @param target 被关注人
     * @return UserFollow 返回的关注表
     */
    public static UserFollow getUserFollow(final User origin,final User target){
        return Hib.query(session -> {
            return (UserFollow) session.createQuery("from UserFollow where originId = :originId and targetId = :targetId")
                    .setParameter("originId",origin.getId())
                    .setParameter("targetId",target.getId())
                    .setMaxResults(1)
                    .uniqueResult(); //查询一条数据
        });
    }

    /**
     * 搜索联系人的实现
     * @param name 查询的name允许为空
     * @return 查询到的用户集合，如果name为空则返回最近的用户
     */
    @SuppressWarnings("unchecked")
    public static List<User> search(String name) {
        if (Strings.isNullOrEmpty(name))
            name = ""; //保证不是null的情况，减少额外错误
        final String searchName = "%" + name + "%"; // 模糊匹配
        return Hib.query(session -> {
            //查询条件：name忽略大小写并使用模糊查询，必须有描述和头像才能查到
            return (List<User>) session.createQuery("from User where lower(name) like :name and portrait is not null and description is not null")
                    .setParameter("name", searchName)
                    .setMaxResults(20) // 至多20条
                    .list();
        });
    }
}