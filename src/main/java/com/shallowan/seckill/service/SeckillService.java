package com.shallowan.seckill.service;

import com.shallowan.seckill.domain.OrderInfo;
import com.shallowan.seckill.domain.SeckillOrder;
import com.shallowan.seckill.domain.SeckillUser;
import com.shallowan.seckill.redis.RedisService;
import com.shallowan.seckill.redis.SeckillKey;
import com.shallowan.seckill.redis.SeckillUserKey;
import com.shallowan.seckill.util.MD5Util;
import com.shallowan.seckill.util.UUIDUtil;
import com.shallowan.seckill.vo.GoodsVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Random;

/**
 * @author ShallowAn
 */
@Service
public class SeckillService {
    @Autowired
    private GoodsService goodsService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private RedisService redisService;

    @Transactional
    public OrderInfo seckill(SeckillUser seckillUser, GoodsVO goods) {
        //从数据库减库存
        boolean success = goodsService.reduceStock(goods);
        //在数据库中创建订单数据，并写入redis，并返回；检查是否秒杀到了时使用
        if (success) {
            return orderService.createOrder(seckillUser, goods);
        }
        //到这里，库存被消耗完了，设置商品秒杀完毕
        setGoodsOver(goods.getId());
        return null;

    }

    /**
     *
     * @param userId 用户id
     * @param goodsId 商品id
     * @return 用户是否秒杀到商品
     */
    public long getSeckillResult(Long userId, long goodsId) {
        //从库里查询是否秒杀到
        SeckillOrder seckillOrder = orderService.getSeckillOrderByUserIdGoodsId(userId, goodsId);

        //秒杀成功，返回订单id
        if (seckillOrder != null) {
            return seckillOrder.getOrderId();
        }
        //没有秒杀成功，查看商品是否结束
        boolean isOver = getGoodsOver(goodsId);
        if (isOver) {
            //秒杀已经结束
            return -1;
        } else {
            //秒杀还没有结束
            return 0;
        }

    }

    private boolean getGoodsOver(long goodsId) {
        return redisService.exists(SeckillKey.isGoodsOver, "" + goodsId);
    }

    public void setGoodsOver(Long goodsId) {
        redisService.set(
                SeckillKey.isGoodsOver,
                "" + goodsId,
                true
        );
    }

    public void reset(List<GoodsVO> goodsVOList) {
        goodsService.resetStock(goodsVOList);
        orderService.deleteOrders();
    }

    public boolean checkPath(SeckillUser seckillUser, long goodsId, String path) {
        if (seckillUser == null || path == null) {
            return false;
        }
        String pathOld = redisService.get(SeckillKey.getSeckillPath, "" + seckillUser.getId() + "_" + goodsId, String.class);
        return path.equals(pathOld);
    }

    public String createSeckillPath(SeckillUser seckillUser, long goodsId) {
        String str = MD5Util.md5(UUIDUtil.uuid() + "123456");
        redisService.set(
                SeckillKey.getSeckillPath,
                "" + seckillUser.getId() + "_" + goodsId,
                str);
        return str;
    }

    public BufferedImage createVerifyCode(SeckillUser seckillUser, long goodsId) {
        if (seckillUser == null || goodsId < 0) {
            return null;
        }
        int width = 80;
        int height = 32;
        //create the image
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics g = image.getGraphics();
        // set the background color
        g.setColor(new Color(0xDCDCDC));
        g.fillRect(0, 0, width, height);
        // draw the border
        g.setColor(Color.black);
        g.drawRect(0, 0, width - 1, height - 1);
        // create a random instance to generate the codes
        Random rdm = new Random();
        // make some confusion
        for (int i = 0; i < 50; i++) {
            int x = rdm.nextInt(width);
            int y = rdm.nextInt(height);
            g.drawOval(x, y, 0, 0);
        }
        // generate a random code
        String verifyCode = generateVerifyCode(rdm);
        g.setColor(new Color(0, 100, 0));
        g.setFont(new Font("Candara", Font.BOLD, 24));
        g.drawString(verifyCode + "=", 8, 24);
        g.dispose();
        //把验证码存到redis中
        int rnd = calc(verifyCode);
        redisService.set(SeckillKey.getSeckillVerifyCode,
                seckillUser.getId() + "," + goodsId,
                rnd);
        //输出图片
        return image;
    }

    public boolean checkVerifyCode(SeckillUser user, long goodsId, int verifyCode) {
        if (user == null || goodsId <= 0) {
            return false;
        }
        Integer codeOld = redisService.get(SeckillKey.getSeckillVerifyCode, user.getId() + "," + goodsId, Integer.class);
        if (codeOld == null || codeOld - verifyCode != 0) {
            return false;
        }
        redisService.delete(SeckillKey.getSeckillVerifyCode, user.getId() + "," + goodsId);
        return true;
    }

    private static int calc(String exp) {
        try {
            ScriptEngineManager manager = new ScriptEngineManager();
            ScriptEngine engine = manager.getEngineByName("JavaScript");
            return (Integer) engine.eval(exp);
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    private static char[] ops = new char[]{'+', '-', '*'};

    /**
     * + - *
     */
    private String generateVerifyCode(Random rdm) {
        int num1 = rdm.nextInt(10);
        int num2 = rdm.nextInt(10);
        int num3 = rdm.nextInt(10);
        char op1 = ops[rdm.nextInt(3)];
        char op2 = ops[rdm.nextInt(3)];
        String exp = "" + num1 + op1 + num2 + op2 + num3;
        System.out.println(exp);
        return exp;
    }
}
