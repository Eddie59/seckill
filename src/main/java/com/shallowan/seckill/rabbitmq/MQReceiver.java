package com.shallowan.seckill.rabbitmq;

import com.shallowan.seckill.domain.OrderInfo;
import com.shallowan.seckill.domain.SeckillOrder;
import com.shallowan.seckill.domain.SeckillUser;
import com.shallowan.seckill.redis.RedisService;
import com.shallowan.seckill.result.CodeMsg;
import com.shallowan.seckill.result.Result;
import com.shallowan.seckill.service.GoodsService;
import com.shallowan.seckill.service.OrderService;
import com.shallowan.seckill.service.SeckillService;
import com.shallowan.seckill.vo.GoodsVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author ShallowAn
 */
@Service
@Slf4j
public class MQReceiver {
    @Autowired
    private GoodsService goodsService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private SeckillService seckillService;


    /**
     *
     * 消费时，查库存、减库存、添加订单都是在数据库操作的，然后把数据输出到redis供结果查询
     */
    @RabbitListener(queues = MQConfig.SECKILL_QUEUE)
    public void receive(String message) {
        log.info("receive message:" + message);

        SeckillMessage seckillMessage = RedisService.stringToBean(message, SeckillMessage.class);
        SeckillUser user = seckillMessage.getSeckillUser();
        long goodsId = seckillMessage.getGoodsId();

        //去数据库判断该商品的库存
        GoodsVO goods = goodsService.getGoodsVOById(goodsId);
        int stock = goods.getStockCount();
        if (stock < 1) {
            return;
        }

        //有库存时，判断是否已经秒杀到了
        SeckillOrder order = orderService.getSeckillOrderByUserIdGoodsId(user.getId(), goodsId);
        if (order != null) {
            return;
        }

        //没有秒杀到时，数据库减库存数量并创建订单，在Redis中设置该商品秒杀结束
        seckillService.seckill(user, goods);
    }

//    @RabbitListener(queues = MQConfig.QUEUE)
//    public void receive(String message) {
//        log.info("receive message:" + message);
//    }
//
//    @RabbitListener(queues = MQConfig.TOPIC_QUEUE1)
//    public void receiveTopic1(String message) {
//        log.info("topic1 message:" + message);
//    }
//
//    @RabbitListener(queues = MQConfig.TOPIC_QUEUE2)
//    public void receiveTopic2(String message) {
//        log.info("topic2 message:" + message);
//    }
//
//    @RabbitListener(queues = MQConfig.HEADER_QUEUE)
//    public void receiveHeader(byte[] message) {
//        log.info("headers queue message:" + new String(message));
//    }

}
