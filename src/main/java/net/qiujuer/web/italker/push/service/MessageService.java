package net.qiujuer.web.italker.push.service;

import net.qiujuer.web.italker.push.bean.api.base.ResponseModel;
import net.qiujuer.web.italker.push.bean.api.message.MessageCreateModel;
import net.qiujuer.web.italker.push.bean.card.MessageCard;
import net.qiujuer.web.italker.push.bean.db.Group;
import net.qiujuer.web.italker.push.bean.db.Message;
import net.qiujuer.web.italker.push.bean.db.User;
import net.qiujuer.web.italker.push.factory.GroupFactory;
import net.qiujuer.web.italker.push.factory.MessageFactory;
import net.qiujuer.web.italker.push.factory.PushFactory;
import net.qiujuer.web.italker.push.factory.UserFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

@Path("/msg")
public class MessageService extends BaseService{

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ResponseModel<MessageCard> pushMessage(MessageCreateModel model){
        if (!MessageCreateModel.check(model))
            return ResponseModel.buildParameterError();

        User self = getSelf();  //获取到sender 用拦截器拿到token载拿到自己

        //查询数据库是否有这个消息
        Message message = MessageFactory.findById(model.getId());
        if (message!=null)
            return ResponseModel.buildOk(new MessageCard(message));
        if (model.getReceiverType()==Message.RECEIVER_TYPE_GROUP){
            return pushToGroup(self,model);
        }else {
            return pushToUser(self,model);
        }
    }


    private ResponseModel<MessageCard> pushToUser(User sender, MessageCreateModel model) {
        User receiver = UserFactory.findById(model.getReceiverId());
        //接收者Id检查
        if (receiver==null)
            return ResponseModel.buildNotFoundUserError("Can't find receiver user");
        //接收者不能是自己
        if (receiver.getId().equalsIgnoreCase(sender.getId()))
            return ResponseModel.buildCreateError(ResponseModel.ERROR_CREATE_MESSAGE);

        //添加到数据库
        Message message = MessageFactory.add(sender,receiver,model);
        return buildAndPushResponse(sender,message);
}



    private ResponseModel<MessageCard> pushToGroup(User sender, MessageCreateModel model) {
        Group group = GroupFactory.findById(sender,model.getReceiverId());//发送者必须要在群里面
        if (group==null){
            return ResponseModel.buildNotFoundUserError("can't find receiver group");
        }
        //存储到数据库
        Message message = MessageFactory.add(sender,group,model);
        return buildAndPushResponse(sender,message);
    }

    private ResponseModel<MessageCard> buildAndPushResponse(User sender, Message message) {
        if (message==null){
            //数据库存储失败
            return ResponseModel.buildCreateError(ResponseModel.ERROR_CREATE_MESSAGE);
        }
        PushFactory.pushNewMessage(sender,message);

        return ResponseModel.buildOk(new MessageCard(message));
    }

}
