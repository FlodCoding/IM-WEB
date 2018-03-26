package net.qiujuer.web.italker.push.provider;

import com.google.common.base.Strings;
import net.qiujuer.web.italker.push.bean.api.base.ResponseModel;
import net.qiujuer.web.italker.push.bean.db.User;
import net.qiujuer.web.italker.push.factory.UserFactory;
import org.glassfish.jersey.server.ContainerRequest;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.security.Principal;

/**
 * 用于所有的请求接口拦截和过滤
 */
@Provider
public class AuthRequestFilter implements ContainerRequestFilter{

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        //检测是否是登陆注册接口，检查路径
        String relationPath = ((ContainerRequest)requestContext).getPath(false);
        if (relationPath.startsWith("account/login")
                || relationPath.startsWith("account/register")){
            return; //如果是上面的路径就直接返回
        }


        //从Headers中找的第一个token
        String token = requestContext.getHeaders().getFirst("token");
        if (!Strings.isNullOrEmpty(token)){

            //查询自己的信息
            final User self = UserFactory.findByToken(token);
            if(self!=null){
                //给当前请求添加一个一个上下文
                requestContext.setSecurityContext(new SecurityContext() {
                    //主体部分
                    @Override
                    public Principal getUserPrincipal() {
                        return self;
                    }

                    //是否用户在规则中，可以在这里加入用户权限
                    //比如管理员权限
                    @Override
                    public boolean isUserInRole(String role) {
                        return true;
                    }

                    @Override
                    public boolean isSecure() {
                        //默认false，这里检查的是Https
                        return false;
                    }

                    @Override
                    public String getAuthenticationScheme() {
                        return null;
                    }
                });
                return;
            }
        }

        //除此之外的（没有token）
        ResponseModel model = ResponseModel.buildAccountError();

        //构建一个返回
        Response response = Response.status(Response.Status.OK)
                .entity(model)
                .build();
        //停止一个请求，调用后直接返回不会走到Serice
        requestContext.abortWith(response); //中断
    }
}
