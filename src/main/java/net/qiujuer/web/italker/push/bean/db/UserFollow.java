package net.qiujuer.web.italker.push.bean.db;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.PrimaryKeyJoinColumn;
import javax.persistence.Table;

/**
 * 用户关系的Model
 * 用于用户之间好友的关系
 */
@Entity
@Table(name = "TB_USER_FOLLOW")
public class UserFollow {
    @Id
    @PrimaryKeyJoinColumn
    @GeneratedValue(generator = "uuid")
    @GenericGenerator(name = "uuid",strategy = "uuid2")
    @Column(updatable = false,nullable = false)
    private String id;


    //定义一个发起者，你关注某人，这个就是你
    //多对一 你可以关注很多人，可以创建很多个关注信息
    //这里的User 对应的是多个的UserFollow
    //optional = false 不可选的，必须是存在的，一条关注记录必须要有一个”你“
    @ManyToOne(optional = false)   //Many指UserFollow，One指User
    //定义关联表字段名为originId，对应的是User.id
    @JoinColumn(name = "originId")
    private User origin;
    //把originId直接提取到model中。
    @Column(nullable = false,updatable = false,insertable = false)
    private String originId;

    //定义关注的目标
    //你可以被很多人关注
    @ManyToOne(optional = false)
    //定义关联表字段名为targetId，对应的是User.id，
    // 不加name是target_id,这样就可以用下面那个列来接收
    @JoinColumn(name = "targetId")
    private User target;
    @Column(nullable = false,updatable = false,insertable = false)
    private String targetId;

    //备注，对target的备注，可为空
    @Column
    private String alias;

    //定义为创建时间戳，在创建是就会被写入
    @CreationTimestamp
    @Column(nullable = false)
    private LocalDateTime createAt = LocalDateTime.now();

    //更新时间戳
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updateAt = LocalDateTime.now();



    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public User getOrigin() {
        return origin;
    }

    public void setOrigin(User origin) {
        this.origin = origin;
    }

    public String getOriginId() {
        return originId;
    }

    public void setOriginId(String originId) {
        this.originId = originId;
    }

    public User getTarget() {
        return target;
    }

    public void setTarget(User target) {
        this.target = target;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public LocalDateTime getCreateAt() {
        return createAt;
    }

    public void setCreateAt(LocalDateTime createAt) {
        this.createAt = createAt;
    }

    public LocalDateTime getUpdateAt() {
        return updateAt;
    }

    public void setUpdateAt(LocalDateTime updateAt) {
        this.updateAt = updateAt;
    }

}
