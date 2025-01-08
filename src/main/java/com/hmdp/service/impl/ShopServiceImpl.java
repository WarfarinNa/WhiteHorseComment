package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        //解决缓存穿透
       Shop shop = cacheClient
               .queryWithPassThrough(CACHE_SHOP_KEY,id,Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);

        //互斥锁解决缓存击穿
        //Shop shop = queryWithMutex(id);

        //逻辑过期解决缓存击穿
       // Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY,id, Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);
        if (shop == null) {
            return Result.fail("店铺不存在！");
        }
        return Result.ok(shop);
    }
/*    //线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);*/

 /*   public Shop queryWithLogicalExpire(Long id){
        //缓存永不过期，由逻辑锁进行判断是否过期
        String key = CACHE_SHOP_KEY+id;
        //从redis中查数据
        String json = stringRedisTemplate.opsForValue().get(key);
        //判断redis中存在数据
        if(StrUtil.isBlank(json)){
            return null;
        }
        //命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        //判断是否过期
        //未过期，直接返回店铺信息
        if (expireTime.isAfter(LocalDateTime.now())) {
            return shop;
        }
        //已过期，进行缓存重建
        //缓存重建
        //获取互斥锁
        String lockKey = LOCK_SHOP_KEY+id;
        boolean isLock = tryLock(lockKey);
        //判断是否获取锁成功
        if (isLock) {
            //获取锁成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    //重建缓存
                    this.saveShop2Redis(id,20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unlock(lockKey);
                }
            });
        }
        //获取锁失败，返回过期的店铺信息
        return shop;
    }*/

/*    public Shop queryWithMutex(Long id){
        String key = CACHE_SHOP_KEY+id;
        //从redis中查数据
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //判断redis中存在数据
        if(StrUtil.isNotBlank(shopJson)){
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //判断命中的是否为空,！=null等价于字符串为""
        if (shopJson!=null) {
            return null;
        }
        //实现缓存重建
        //获取互斥锁
        String lockKey = "lock:shop"+id;
        Shop shop = null;
        try {
            boolean isLock = tryLock(lockKey);
            //判断是否获取成功
            if(!isLock){
                //失败，则休眠并重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //成功
            // 判断redis中不存在数据，数据也不为空，根据id查询数据库,
            shop = getById(id);

            //模拟重建延迟
            Thread.sleep(200);

            //不存在，返回错误
            if(shop==null){
                //将空值写入redis，再返回错误信息解决缓存穿透
                stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
                return null;
            }
            //存在数据时写入redis
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        }catch (Exception e){
            throw new RuntimeException(e);
        }finally {
            //释放互斥锁
            unlock(lockKey);
        }
        return shop;
    }*/

/*    public Shop queryWithPassThrough(Long id){
        String key = CACHE_SHOP_KEY+id;
        //从redis中查数据
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //判断redis中存在数据
        if(StrUtil.isNotBlank(shopJson)){
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //判断命中的是否为空,！=null等价于字符串为""
        if (shopJson!=null) {
            return null;
        }

        //判断redis中不存在数据，数据也不为空
        Shop shop = getById(id);
        if(shop==null){
            //将空值写入redis再返回错误信息
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        //redis数据不存在时，将数据时写入redis
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }*/

/*    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key,"1",10,TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);//包装类拆箱可能会报空指针异常，使用工具类返回
    }

    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

    //单元测试-缓存逻辑过期用
    public void saveShop2Redis(Long id,Long expireSeconds) throws InterruptedException {
        //查数据
        Shop shop = getById(id);
        //模拟延迟
        Thread.sleep(200);
        //封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));

    }*/

    @Override
    @Transactional
    //采用删除策略解决双写问题，先操作数据库，再删除缓存
    public Result update(Shop shop) {
        //更新数据库
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        updateById(shop);
        //删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY+id);
        return Result.ok();
    }
}
