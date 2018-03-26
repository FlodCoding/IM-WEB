package net.qiujuer.web.italker.push.service;

import com.google.common.base.Strings;
import net.qiujuer.web.italker.push.bean.api.account.AccountRspModel;
import net.qiujuer.web.italker.push.bean.api.account.LoginModel;
import net.qiujuer.web.italker.push.bean.api.account.RegisterModel;
import net.qiujuer.web.italker.push.bean.api.base.ResponseModel;
import net.qiujuer.web.italker.push.bean.card.UserCard;
import net.qiujuer.web.italker.push.bean.db.User;
import net.qiujuer.web.italker.push.factory.UserFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

@Path("/account")
public class AccountService extends BaseService {
    @POST
    @Path("/login")
    // 指定请求与返回的相应体为JSON
    @Consumes(MediaType.APPLICATION_JSON)   //传入的是json
    @Produces(MediaType.APPLICATION_JSON)   //返回的是json
    public ResponseModel<AccountRspModel> login(LoginModel model) {
        //检查是否用户或者密码有为空
        if (!LoginModel.check(model)) {
            return ResponseModel.buildParameterError();
        }
        User user = UserFactory.login(model.getAccount(), model.getPassword());
        if (user != null) {

            //如果model有携带一个PushId进来，就把这个PushId进行绑定
            //同时，AccountRspModel的isBand=true
            if (!Strings.isNullOrEmpty(model.getPushId())) {
                return bind(user,model.getPushId());
            }


            //result 是 user = new UserCard(user);  没有包含密码，token的敏感信息
            AccountRspModel rspModel = new AccountRspModel(user);
            return ResponseModel.buildOk(rspModel);
        } else {
            return ResponseModel.buildLoginError();
        }
    }

    @POST
    @Path("/register")
    // 指定请求与返回的相应体为JSON
    @Consumes(MediaType.APPLICATION_JSON)   //传入的是json
    @Produces(MediaType.APPLICATION_JSON)   //返回的是json
    public ResponseModel<AccountRspModel> register(RegisterModel model) {

        if (!RegisterModel.check(model)) {
            return ResponseModel.buildParameterError();
        }

        User user = UserFactory.findByPhone(model.getAccount().trim());
        if (user != null) {
            //已有账户
            return ResponseModel.buildHaveAccountError();
        }

        user = UserFactory.findByName(model.getName().trim());
        if (user != null) {
            //已有该用户名
            return ResponseModel.buildHaveNameError();
        }

        //开始注册
        //核心操作，保存到数据库，然后进行登陆然后绑定设备
        user = UserFactory.register(model.getAccount(),
                model.getPassword(),
                model.getName());

        if (user != null) {
            if (!Strings.isNullOrEmpty(model.getPushId())) {
                return bind(user, model.getPushId());
            }
        }

        if (user != null) {
            //返回当前账户
            AccountRspModel rspModel = new AccountRspModel(user);
            return ResponseModel.buildOk(rspModel);
        } else {
            return ResponseModel.buildRegisterError();
        }
    }


    @POST
    @Path("/bind/{pushId}")
    // 指定请求与返回的相应体为JSON
    @Consumes(MediaType.APPLICATION_JSON)   //传入的是json
    @Produces(MediaType.APPLICATION_JSON)   //返回的是json
    //从请求头中获取token字段
    //从url地中中获取pushId ↑
    //@HeaderParam("token") String token可以取消掉了，过滤器已经使用到了
    public ResponseModel<AccountRspModel> bind
            (@HeaderParam("token") String token, @PathParam("pushId") String pushId) {
        //检查是否用户或者密码有为空
        if (Strings.isNullOrEmpty(token) ||
                Strings.isNullOrEmpty(pushId)) {
            return ResponseModel.buildParameterError();
        }
        //拿到当前自己信息
        User user = getSelf();
        return bind(user, pushId);
    }

    /**
     * 绑定的操作
     * @param self   自己
     * @param pushId PushId
     * @return User
     */
    private ResponseModel<AccountRspModel> bind(User self, String pushId) {
        // 进行设备Id绑定的操作
        //核心操作，
        User user = UserFactory.bindPushId(self, pushId);

        if (user == null) {
            // 绑定失败则是服务器异常
            return ResponseModel.buildServiceError();

        }

        // 返回当前的账户, 并且已经绑定了
        AccountRspModel rspModel = new AccountRspModel(user, true);
        return ResponseModel.buildOk(rspModel);

    }
}
