package com.qiwenshare.file.service;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qiwenshare.common.cbb.DateUtil;
import com.qiwenshare.common.cbb.RestResult;
import com.qiwenshare.common.util.JjwtUtil;
import com.qiwenshare.common.util.PasswordUtil;
import com.qiwenshare.file.api.IUserService;
import com.qiwenshare.file.controller.UserController;
import com.qiwenshare.file.domain.UserBean;
import com.qiwenshare.file.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.crypto.hash.SimpleHash;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class UserService extends ServiceImpl<UserMapper, UserBean> implements IUserService {
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    @Resource
    UserMapper userMapper;

    @Override
    public UserBean getUserBeanByToken(String token){
        Claims c = null;
        try {
            logger.debug("token:" + token);
            c = JjwtUtil.parseJWT(token);
        } catch (Exception e) {
            logger.error("解码异常");
            e.printStackTrace();
            return null;
        }
        if (c == null) {
            logger.info("解码为空");
            return null;
        }
        String subject = c.getSubject();
        logger.debug("解析结果：" + subject);
        UserBean tokenUserBean = JSON.parseObject(subject, UserBean.class);

        UserBean saveUserBean = new UserBean();
        String tokenPassword = "";
        String savePassword = "";
        if (StringUtils.isNotEmpty(tokenUserBean.getPassword())) {
            saveUserBean = findUserInfoByTelephone(tokenUserBean.getTelephone());
            tokenPassword = tokenUserBean.getPassword();
            savePassword = saveUserBean.getPassword();
        } else if (StringUtils.isNotEmpty(tokenUserBean.getQqPassword())) {
            saveUserBean = selectUserByopenid(tokenUserBean.getOpenId());
            tokenPassword = tokenUserBean.getQqPassword();
            savePassword = saveUserBean.getQqPassword();
        }
        if (StringUtils.isEmpty(tokenPassword) || StringUtils.isEmpty(savePassword)) {
            return null;
        }
        if (tokenPassword.equals(savePassword)) {

            return saveUserBean;
        } else {
            return null;
        }
    }


    @Override
    public UserBean selectUserByopenid(String openid) {
        UserBean userBean = userMapper.selectUserByopenId(openid);
        return userBean;
    }
    /**
     * 用户注册
     */
    @Override
    public RestResult<String> registerUser(UserBean userBean) {
        RestResult<String> restResult = new RestResult<String>();
        //判断验证码
        String telephone = userBean.getTelephone();
//        String saveVerificationCode = UserController.verificationCodeMap.get(telephone);
//        if (!saveVerificationCode.equals(userBean.getVerificationcode())){
//            restResult.setSuccess(false);
//            restResult.setErrorMessage("验证码错误！");
//            return restResult;
//        }
        UserController.verificationCodeMap.remove(telephone);
        if (userBean.getTelephone() == null || "".equals(userBean.getTelephone())){
            restResult.setSuccess(false);
            restResult.setErrorMessage("用户名不能为空！");
            return restResult;
        }
        if (userBean.getPassword() == null || "".equals(userBean.getPassword())){
            restResult.setSuccess(false);
            restResult.setErrorMessage("密码不能为空！");
            return restResult;
        }

        if (userBean.getUsername() == null || "".equals(userBean.getUsername())){
            restResult.setSuccess(false);
            restResult.setErrorMessage("用户名不能为空！");
            return restResult;
        }
        if (isUserNameExit(userBean)) {
            restResult.setSuccess(false);
            restResult.setErrorMessage("用户名已存在！");
            return restResult;
        }
        if (!isPhoneFormatRight(userBean.getTelephone())){
            restResult.setSuccess(false);
            restResult.setErrorMessage("手机号格式不正确！");
            return restResult;
        }
        if (isPhoneExit(userBean)) {
            restResult.setSuccess(false);
            restResult.setErrorMessage("手机号已存在！");
            return restResult;
        }


        String salt = PasswordUtil.getSaltValue();
        String newPassword = new SimpleHash("MD5", userBean.getPassword(), salt, 1024).toHex();

        userBean.setSalt(salt);

        userBean.setPassword(newPassword);
        userBean.setRegisterTime(DateUtil.getCurrentTime());
        int result = userMapper.insertUser(userBean);
        userMapper.insertUserRole(userBean.getUserId(), 2);
//        UserImageBean userImageBean = new UserImageBean();
//        userImageBean.setImageUrl("");
//        userImageBean.setUserId(userBean.getUserId());
        if (result == 1) {
            restResult.setSuccess(true);
            return restResult;
        } else {
            restResult.setSuccess(false);
            restResult.setErrorCode("100000");
            restResult.setErrorMessage("注册用户失败，请检查输入信息！");
            return restResult;
        }
    }

    /**
     * 添加用户
     */
    @Override
    public UserBean addUser(UserBean userBean) {

        String salt = PasswordUtil.getSaltValue();
        String newPassword = new SimpleHash("MD5", userBean.getOpenId(), salt, 1024).toHex();

        userBean.setSalt(salt);
        userBean.setQqPassword(newPassword);

        userMapper.insertUser(userBean);
        userMapper.insertUserRole(userBean.getUserId(), 2);
        return userBean;
    }

  /**
     * 检测用户名是否存在
     *
     * @param userBean
     */
    private Boolean isUserNameExit(UserBean userBean) {
        UserBean result = userMapper.selectUserByUserName(userBean);
        if (result != null) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * 检测手机号是否存在
     *
     * @param userBean
     * @return
     */
    private Boolean isPhoneExit(UserBean userBean) {
        UserBean result = userMapper.selectUserByTelephone(userBean);
        if (result != null) {
            return true;
        } else {
            return false;
        }
    }

    private Boolean isPhoneFormatRight(String phone){
        String regex = "^1\\d{10}";
        boolean isRight = Pattern.matches(regex, phone);
        return isRight;
    }

    /**
     * 通过用户名获取用户信息
     *
     * @param userName
     * @return
     */
    public UserBean findUserInfoByName(String userName) {
        UserBean userinfo = new UserBean();
        userinfo.setUsername(userName);
        return userMapper.selectUserByUserName(userinfo);
    }

    /**
     * 通过手机号获取用户信息
     *
     * @param telephone
     * @return
     */
    public UserBean findUserInfoByTelephone(String telephone) {
        UserBean userinfo = new UserBean();
        userinfo.setTelephone(telephone);
        return userMapper.selectUserByTelephone(userinfo);
    }

    /**
     * 通过用户名获取用户信息
     *
     * @param userName
     * @return
     */
    public UserBean findUserInfoByNameAndPassword(String userName, String password) {
        UserBean userinfo = new UserBean();
        userinfo.setUsername(userName);
        return userMapper.selectUserByUserName(userinfo);
    }

    /**
     * 用户登录
     */
    @Override
    public UserBean loginUser(UserBean userBean) {
        RestResult<UserBean> restResult = new RestResult<UserBean>();
        if (userBean.getUsername() == null && userBean.getTelephone() != null) {
            userBean.setUsername(userBean.getTelephone());
        }
        if (userBean.getUsername() != null && userBean.getTelephone() == null) {
            userBean.setTelephone(userBean.getUsername());
        }
        UserBean result = userMapper.selectUser(userBean);

        return result;
    }

    @Override
    public List<UserBean> selectAdminUserList() {
        return userMapper.selectAdminUserList();
    }



    @Override
    public UserBean getUserInfoById(long userId) {
        UserBean userBean = userMapper.selectUserById(userId);

        return userBean;
    }

    @Override
    public UserBean selectUserByopenId(String openId) {
        UserBean userBean = userMapper.selectUserByopenId(openId);
        return userBean;
    }


    /**
     * 修改用户信息
     */
    @Override
    public RestResult<String> updateUserInfo(UserBean userBean) {
        RestResult<String> restResult = new RestResult<String>();
        userMapper.updateUserInfo(userBean);

        restResult.setSuccess(true);
        return restResult;
    }

    @Override
    public void updateEmail(UserBean userBean) {
        userMapper.updateEmail(userBean);
    }

    @Override
    public void updataImageUrl(UserBean userBean){
        userMapper.updataImageUrl(userBean);
    }

    /**
     * 查询所有的用户
     */
    @Override
    public List<UserBean> selectAllUserList() {
        return userMapper.selectAllUserList();
    }





    @Override
    public void deleteUserInfo(UserBean userBean) {
        userMapper.deleteUserInfo(userBean);

    }

}
