package net.qiujuer.web.italker.push.service;

import com.google.common.base.Strings;
import net.qiujuer.web.italker.push.bean.api.base.ResponseModel;
import net.qiujuer.web.italker.push.bean.api.user.UpdateInfoModel;
import net.qiujuer.web.italker.push.bean.card.UserCard;
import net.qiujuer.web.italker.push.bean.db.User;
import net.qiujuer.web.italker.push.factory.PushFactory;
import net.qiujuer.web.italker.push.factory.UserFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户信息处理
 */


//用来更新用户的信息，
@Path("/user")
public class UserService extends BaseService {

    @PUT  //路径：api/user
    @Consumes(MediaType.APPLICATION_JSON)   //传入的是json
    @Produces(MediaType.APPLICATION_JSON)   //返回的是json
    public ResponseModel<UserCard> update( UpdateInfoModel model) {
        if (!UpdateInfoModel.check(model)) { //只要name portrait desc sex 有一个更改就可以了
            return ResponseModel.buildParameterError();
        }
        User self = getSelf(); //解决每次在请求头中拿token，再然后查询自己的麻烦
        //更新用户信息
        self = model.updateToUser(self);
        self = UserFactory.update(self);
        UserCard card = new UserCard(self, true); //默认关注了自己
        ResponseModel.buildOk(card);
        return ResponseModel.buildOk(card);

    }


    //用来拉取联系人
    @GET
    @Path("/contact")  //路径：api/user/contact
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ResponseModel<List<UserCard>> contact(){
        User self = getSelf();

        List<User>  users = UserFactory.contact(self);
        List<UserCard> userCards = users.stream()  //进行转置操作，将user类转换为usercard
                .map(user -> new UserCard(user,true))
                .collect(Collectors.toList()); //转换为List

        return ResponseModel.buildOk(userCards);
    }


    //关注人,是修改操作
    //简化为：关注别人，同时别人也关注你
    @PUT
    @Path("/follow/{followId}")  //路径：api/user/follow/{followId} 与下面对应上
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ResponseModel<UserCard> follow (@PathParam("followId") String followId){
        User self = getSelf();

        //判断关注的是不是自己
        if (self.getId().equalsIgnoreCase(followId)) { //equalsIgnoreCase不区分大小写
            return ResponseModel.buildParameterError();//返回参数异常
        }
        //找到我要关注的人的Id
        User target = UserFactory.findById(followId);
        if (target == null){ //如果没找到就返回错误
            return ResponseModel.buildNotFoundUserError(null);
        }
        //默认没有备注
        target = UserFactory.follow(self,target,null);
        if(target == null){
            return ResponseModel.buildServiceError();
        }

        //TODO 通知我关注的人，我关注了他
        PushFactory.pushFollow(target,new UserCard(self));

        return ResponseModel.buildOk(new UserCard(target,true));
        }

        //用用户id获取某人的信息
        @GET
        @Path("{id}")  //api/user/{id}
        @Consumes(MediaType.APPLICATION_JSON)
        @Produces(MediaType.APPLICATION_JSON)
    public  ResponseModel<UserCard> getUser(@PathParam("id") String id){
        if (Strings.isNullOrEmpty(id)){
            //返回参数异常
            return ResponseModel.buildParameterError();
        }

        User self = getSelf();
        if (self.getId().equalsIgnoreCase(id)){
            //如果找的Id是自己，那就返回自己不必查询
            return ResponseModel.buildOk(new UserCard(self,true));
        }
        User user = UserFactory.findById(id);
        if (user == null){
            //找到的是空就返回没找到异常
            return ResponseModel.buildNotFoundUserError(null);
        }

            //如果存在这条关注信息，就认为已经关注过了
        boolean isFollow = UserFactory.getUserFollow(self,user) != null;
        return ResponseModel.buildOk(new UserCard(user,isFollow));
    }


    //搜索名字
    @GET    //api/user/search/
    @Path("/search/{name:(.*?)}")  //正则：名字任意字符可以为空
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)//@DefaultValue("")默认值为空
    public ResponseModel<List<UserCard>> search(@DefaultValue("")@PathParam("name") String name) {
        User self = getSelf();

         List<User> searchUsers = UserFactory.search(name);
        if (searchUsers == null){
            //找到的是空就返回没找到异常
            return ResponseModel.buildNotFoundUserError(null);
        }

        //获取我的联系人（关注列表）
        final List<User> contact = UserFactory.contact(self);

        //把User转换为UserCard
        List<UserCard> userCards = searchUsers.stream()
                .map(user -> {
                    //判断这人是否是已经在我的联系人中
                    boolean isFollow = user.getId().equalsIgnoreCase(self.getId())
                            || contact.stream().anyMatch( //TODO 7—2 2:7
                                    user1 -> user1.getId().equalsIgnoreCase(user.getId()));

                    return new UserCard(user,isFollow);
                })
                .collect(Collectors.toList());
        return ResponseModel.buildOk(userCards);

    }


}
