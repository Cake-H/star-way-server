package com.smartapply.ida.common.domain.resume;

import java.time.LocalDateTime;
import java.util.Date;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
/**
 * 简历主表(Resume)表实体类
 *
 * @author makejava
 * @since 2026-04-02 20:45:47
 */
@SuppressWarnings("serial")
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("ida_resume")
public class Resume {
    @TableId(type = IdType.AUTO)
    private Long id;
        
    private Long userId;
    //简历名称，可「姓名-学校-岗位」    
    private String title;
    //默认简历，业务层保证每用户至多一条为1    
    private Integer isDefault;
    //姓名    
    private String fullName;
    //0未知 1男 2女    
    private Integer gender;
    //出生日期    
    private Date birthDate;
    //证件类型：身份证/护照等    
    private String idDocType;
    //证件号码（强敏感，务必加密）    
    private String idDocNumber;
    //手机（加密）    
    private String phone;
    //邮箱（加密）    
    private String email;
    //QQ（腾讯系网申常见）    
    private String qq;
    //微信号    
    private String wechat;
    //证件照/头像 URL    
    private String photoUrl;
    //国家/地区    
    private String countryRegion;
    //籍贯    
    private String nativePlace;
    //民族    
    private String ethnicity;
    //政治面貌    
    private String politicalStatus;
    //户口性质（城镇/农村等，按招聘方字典）    
    private String hukouType;
    //现居省/直辖市    
    private String province;
    //现居市    
    private String city;
    //现居区/县    
    private String district;
    //现居城市合并展示（冗余便于搜索/填充）    
    private String currentCity;
    //详细通讯地址    
    private String detailAddress;
    //邮寄地址同现居    
    private Integer mailingSameAsCurrent;
    //邮寄地址（部分校招表单独要）    
    private String mailingAddress;
    //紧急联系人姓名    
    private String emergencyContactName;
    //紧急联系人电话（加密）    
    private String emergencyContactPhone;
    //与本人关系    
    private String emergencyContactRelation;
    //在职/离职/应届等，业务字典    
    private Integer jobSeekingStatus;
    //是否应届毕业生（校招关键字段）    
    private Integer isFreshGraduate;
    //工作年限描述，如「3年」社招常见    
    private String workYears;
    //期望岗位/职能，可 JSON 数组或分隔符    
    private String expectedPositions;
    //期望工作城市，多选可 JSON    
    private String expectedCities;
    //期望行业    
    private String expectedIndustries;
    //期望薪资    
    private String salaryExpectation;
    //可到岗时间    
    private String availability;
    //是否接受岗位/城市调剂    
    private Integer acceptTransfer;
    //自我介绍    
    private String selfIntro;
    //个人优势/核心竞争力（可分点文本或 Markdown）    
    private String strengths;
    //作品集/个人主页链接列表 [{"label":"","url":""}]    
    private String portfolioLinks;
    //各平台差异字段兜底，供 AI 映射    
    private String extraJson;
    //简历结构版本    
    private Integer schemaVersion;
    //乐观锁    
    private Integer rowVersion;
        
    private LocalDateTime deletedAt;
        
    private LocalDateTime createdAt;
        
    private LocalDateTime updatedAt;
}
