package com.sf.lottery.web.user.controller;

import com.alibaba.fastjson.JSON;
import com.sf.lottery.common.dto.JsonResult;
import com.sf.lottery.common.model.User;
import com.sf.lottery.common.utils.ExceptionUtils;
import com.sf.lottery.service.CoupleService;
import com.sf.lottery.service.UserService;
import com.sf.lottery.web.utils.CookiesUtil;
import com.sf.lottery.web.websocket.WebsocketClientFactory;
import com.sf.lottery.web.weixin.domain.UserInfoReturn;
import org.java_websocket.client.WebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author zyt
 * @version 1.0.0
 * @date 2016/12/1.
 */
@Controller
public class UserController {
    private final static Logger log = LoggerFactory.getLogger(UserController.class);
    @Autowired
    private UserService userService;
    @Autowired
    private CoupleService coupleService;

    @Value("${signUp.websocket.address}")
    private String signUpAddress;

    @ResponseBody
    @RequestMapping(value = "/user/getSignedUser", method = RequestMethod.POST)
    public JsonResult<List<User>> getSignedUser() {
        JsonResult<List<User>> result = new JsonResult<>();
        try {
            List<User> Users = userService.getSignedUser();
            result.setData(Users);
        } catch (Exception e) {
            log.warn(ExceptionUtils.getStackTrace(e));
        }
        return result;
    }



    @RequestMapping(value = "/user/login", method = RequestMethod.POST)
    public String login(@RequestParam("sfnum") int sfnum, @RequestParam("sfname") String sfname, HttpServletRequest request, HttpServletResponse response) {
        Cookie cookie = CookiesUtil.getCookieByName(request, "userJson");
        log.info("Cookies: " + cookie.getValue());
        UserInfoReturn userInfoReturn = null;
        try {
            if (cookie != null) {
                userInfoReturn = JSON.parseObject(URLDecoder.decode(cookie.getValue(), "UTF-8"), UserInfoReturn.class);
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return "redirect:/frontend/loginError.html";
        }
        boolean exists = userService.verifyUser(sfnum, sfname);
        if (exists && userInfoReturn != null) {
            boolean isSigned = false;
            try {
                isSigned = userService.isSignedByUserNnm(sfnum);
            } catch (Exception e) {
                e.printStackTrace();
                return "redirect:/frontend/loginError.html";
            }
            if(isSigned){
                return "redirect:/frontend/main.html";
            }
            User user = new User();
            user.setSfNum(sfnum);
            user.setSfName(sfname);
            user.setWxSex(userInfoReturn.getSex());
            user.setWxOpenid(userInfoReturn.getOpenid());
            user.setWxCity(userInfoReturn.getCity());
            user.setWxCountry(userInfoReturn.getCountry());
            user.setWxHeadimgurl(userInfoReturn.getHeadimgurl());
            user.setWxNickname(userInfoReturn.getNickname());
            user.setWxProvince(userInfoReturn.getProvince());
            user.setWxPrivilege(userInfoReturn.getPrivilege());
            user.setWxUnionid(userInfoReturn.getUnionid());
            user.setIsSign(true);
            user.setSignedTime(new Date());
            try {
                userService.saveUser(user);
                User user1 = userService.getUserBySfNumAndName(sfnum, sfname);
                log.info("userInfo:" + user1.toString());
                CookiesUtil.addCookie(response, "userId", String.valueOf(user1.getId()), 86400);
                CookiesUtil.addCookie(response, "flower", "30", 86400);
                CookiesUtil.addCookie(response, "car", "5", 86400);
                CookiesUtil.addCookie(response, "rocket", "1", 86400);
                WebSocketClient webSocketClient = WebsocketClientFactory.getWebsocketClient("signUp", signUpAddress);
                webSocketClient.connectBlocking();
                webSocketClient.send(JSON.toJSONString(user));
                webSocketClient.close();
                return "redirect:/frontend/main.html";
            } catch (Exception e) {
                e.printStackTrace();
                return "redirect:/frontend/loginError.html";
            }
        } else {
            return "redirect:/frontend/loginError.html";
        }
    }

    @ResponseBody
    @RequestMapping(value = "/user/getUserAmount", method = RequestMethod.POST)
    public JsonResult<Map<String, Integer>> getUserAmount() {
        JsonResult<Map<String, Integer>> result = new JsonResult<>();
        try {
            Integer signedAmount = userService.getSignedAmount();
            Integer totalUserAmount = userService.getTotalUserAmount();
            Map<String, Integer> amountMap = new HashMap<>();
            amountMap.put("signed", signedAmount);
            amountMap.put("sum", totalUserAmount);
            result.setData(amountMap);
        } catch (Exception e) {
            log.warn(ExceptionUtils.getStackTrace(e));
        }
        return result;
    }

    @ResponseBody
    @RequestMapping(value = "/user/getUnAwardUser", method = RequestMethod.POST)
    public JsonResult<List<User>> getUnAwardUser() {
        JsonResult<List<User>> result = new JsonResult<>();
        try {
            List<User> unAwardUsers = userService.getUnAwardUser();
            result.setData(unAwardUsers);
        } catch (Exception e) {
            log.warn(ExceptionUtils.getStackTrace(e));
        }
        return result;
    }

    @ResponseBody
    @RequestMapping(value = "/user/deleteWinner", method = RequestMethod.POST)
    public JsonResult<Boolean> deleteWinner(@RequestParam("userId") Integer userId, HttpServletRequest request, HttpServletResponse response) {
        JsonResult<Boolean> result = new JsonResult<>();
        try {
            result.setData(userService.deleteWinner(userId.intValue()));
        } catch (Exception e) {
            log.warn(ExceptionUtils.getStackTrace(e));
        }
        return result;
    }

    @ResponseBody
    @RequestMapping(value = "/cp/cpSubmit", method = RequestMethod.POST)
    public JsonResult<String> cpSubmit(@RequestParam("sfnum1") int sfnum1, @RequestParam("sfnum2") int sfnum2, @RequestParam("imgSrc") String imgSrc, HttpServletRequest request, HttpServletResponse response) throws Exception {
        JsonResult<String> result = new JsonResult<>();
        boolean isCanCpsign = userService.isCanCpsign();
        if (!isCanCpsign) {
            result.setData("false");
            result.setMessage("CP签到已结束，期待明年再会~");
        } else {
            if (sfnum1 == sfnum2) {
                result.setData("false");
                result.setMessage("放你一个人生活？");
            } else {
                boolean isSigned1 = userService.isSignedByUserNnm(sfnum1);
                boolean isSigned2 = userService.isSignedByUserNnm(sfnum2);
                if (isSigned1 && isSigned2) {
                    boolean isCpSigned = userService.cpSign(sfnum1, sfnum2, imgSrc);
                    if (isCpSigned) {
                        result.setData("true");
                        result.setMessage("CP签到成功，有情人终成眷属!");
                    } else {
                        result.setData("false");
                        result.setMessage("你这么到处组，你cp知道么!");
                    }
                } else {
                    result.setData("false");
                    result.setMessage("工号错误或用户未签到");
                }
            }

        }
        return result;
    }

    @ResponseBody
    @RequestMapping(value = "/cp/cpSign", method = RequestMethod.GET)
    public JsonResult<String> cpSign(HttpServletRequest request) throws Exception {
        JsonResult<String> result = new JsonResult<>();
        Cookie cookie = CookiesUtil.getCookieByName(request, "userId");
        String userIdStr = cookie.getValue();
        int userId = Integer.parseInt(userIdStr);
        boolean isCpSign = coupleService.isCpSign(userId);
        if (isCpSign) {
            result.setData("true");
            result.setMessage("CP签到成功，有情人终成眷属!");
        } else {
            result.setData("false");
        }
        return result;
    }

}
