import org.junit.Before;
import org.junit.Test;
import redis.clients.jedis.Jedis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by 81975 on 2017/8/24.
 *
 * Redis数据结构服务器 key-value存储系统
 * 支持存储的数据类型有字符串(String), 哈希(Map), 列表(list), 集合(sets) 和 有序集合(sorted sets)
 *
 *
 */
public class RedisTest {


    private Jedis jedis;

    @Before
    public void init(){
        jedis = new Jedis("localhost",6379);
        System.err.println("Redis Connect Success");
    }


    @Test
    public void stringValue(){
        jedis.set("name","xianhongle");
        System.err.println("得到存储的字符串value:"+jedis.get("name"));
        jedis.append("name"," luoyueyi");//拼接
        System.err.println(jedis.get("name"));
        jedis.del("name");//删除某个键
        //设置多个键值对
        jedis.mset("name","xianhongle","age","23");
    }

    @Test
    public void listValue(){
        //向key为java存入数据
        jedis.lpush("Java","spring","struts","hibernate");
        //向key为Java的list集合取出数据,第一个位置是key,第二个位置是起始位置,第三个位置为结束位置 -1表示取所有
        //jedis.llen获取长度.
        System.err.println(jedis.lrange("Java",0,-1));
    }

    @Test
    public void mapValue(){
        Map<String,String> map = new HashMap<String,String>();
        map.put("Map","123");
        map.put("List","456");
        map.put("String","789");
        jedis.hmset("Object",map);//存储HashMap
        //从HashMap中取数据，第一个参数是HashMap对应的Key，后面key可以多个
        List<String> rsmap = jedis.hmget("Object","Map","List","String");
        System.err.println(rsmap);
        jedis.hdel("Object","String"); //删除map中的某个键
        System.err.println(jedis.hlen("Object"));//返回key为Object的键存放值的个数
        System.err.println(jedis.exists("Object"));//是否存在key为Object的记录
        System.err.println(jedis.hkeys("Object"));//返回map对象的所以key
        System.err.println(jedis.hvals("Object"));//返回map对象中所以value
    }

    @Test
    public void setsValue(){
        jedis.sadd("Set","String");
        jedis.sadd("Set","Integer");
        jedis.sadd("Set","Long");
        jedis.srem("Set","String");//移除
        System.err.println(jedis.smembers("Set"));//获取key为Set的所有value
        System.err.println(jedis.sismember("Set","Long"));//判断某元素是否存在Set中
        System.err.println(jedis.srandmember("Set"));//
        System.err.println(jedis.scard("Set"));//返回集合的元素个数
    }

    @Test
    public void sortedSetsValue(){
        System.err.println(jedis.keys("*"));//查询库当前库中的所有的key
        System.err.println(jedis.ttl("Set"));//用于key没有到期超时 -1。如果键不存在 -2。
        jedis.setex("Set",100,"set");// 通过此方法，可以指定获取键到期的剩余时间(秒)。 如果key的存活（有效时间） 时间为秒
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.err.println(jedis.ttl("Set"));
        jedis.expire("Set", 100);//10秒过期 EXPIRE key seconds 为给定key设置生存时间。当key过期时，它会被自动删除。
        System.err.println(jedis.ttl("Set"));
    }


    @Test
    public void flush(){
        jedis.flushDB();//清空当前DB
        jedis.flushAll();//清空所有DB
    }

}
