package com.shallowan.seckill.access;

import com.alibaba.fastjson.JSON;
import com.shallowan.seckill.domain.SeckillUser;
import com.shallowan.seckill.redis.AccessKey;
import com.shallowan.seckill.redis.RedisService;
import com.shallowan.seckill.result.CodeMsg;
import com.shallowan.seckill.result.Result;
import com.shallowan.seckill.service.SeckillUserService;
import com.shallowan.seckill.util.CookieUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;

/**
 * @author ShallowAn
 */
@Service
public class AccessInterceptor extends HandlerInterceptorAdapter {

    @Autowired
    private SeckillUserService userService;

    @Autowired
    private RedisService redisService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (handler instanceof HandlerMethod) {

            //去拿执行方法的AccessLimit注解
            HandlerMethod method = (HandlerMethod) handler;
            AccessLimit accessLimit = method.getMethodAnnotation(AccessLimit.class);
            if (accessLimit == null) {
                return true;
            }
            int seconds = accessLimit.seconds();
            int maxCount = accessLimit.maxCount();
            boolean needLogin = accessLimit.needLogin();


            //需要登陆，在redis又找不到用户信息时，返回错误码
            SeckillUser seckillUser = getUser(request, response);
            UserContext.setUser(seckillUser);
            String key = request.getRequestURI();
            if (needLogin) {
                if (seckillUser == null) {
                    render(response, CodeMsg.SERVER_ERROR);
                    return false;
                }
                key += "_" + seckillUser.getId();
            }


            //使用Redis的过期时间来做限流，对于一个接口，如果在redis有效时间内超过指定的访问次数，直接返回，不去请求接口
            AccessKey accessKey = AccessKey.withExpire(seconds);
            //查询访问次数
            Integer count = redisService.get(accessKey, key, Integer.class);
            if (count == null) {
                //第一次设置时，会给redis设置“过期时间”
                redisService.set(accessKey, key, 1);
            } else if (count < maxCount) {
                //在过期时间内超过maxCount，会返回频繁访问
                redisService.incr(accessKey, key);
            } else {
                render(response, CodeMsg.ACCESS_LIMIT_REACHED);
                return false;
            }
        }

        return true;
    }

    private void render(HttpServletResponse response, CodeMsg serverError) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        OutputStream outputStream = response.getOutputStream();
        String str = JSON.toJSONString(Result.error(serverError));
        outputStream.write(str.getBytes("UTF-8"));
        outputStream.flush();
        outputStream.close();
    }


    /**
     *
     * @param request
     * @param response
     * @return 从request或者cooke中获取token，拿到token去redis获取用户信息
     */
    private SeckillUser getUser(HttpServletRequest request, HttpServletResponse response) {
        String paramToken = request.getParameter(SeckillUserService.COOKIE_NAME_TOKEN);
        String cookieToken = CookieUtil.getCookieValue(request, SeckillUserService.COOKIE_NAME_TOKEN);
        if (StringUtils.isEmpty(cookieToken) && StringUtils.isEmpty(paramToken)) {
            return null;
        }

        String token = StringUtils.isEmpty(paramToken) ? cookieToken : paramToken;
        return userService.getByToken(response, token);
    }
}
