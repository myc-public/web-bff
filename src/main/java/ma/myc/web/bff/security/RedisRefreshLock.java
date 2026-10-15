package ma.myc.web.bff.security;

import java.time.Duration;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/** Verrou Redis : SET NX PX, liberation atomique par script (compare puis supprime). */
public class RedisRefreshLock implements RefreshLock {

	private static final DefaultRedisScript<Long> UNLOCK = new DefaultRedisScript<>(
			"if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
			Long.class);

	private final StringRedisTemplate redis;

	public RedisRefreshLock(StringRedisTemplate redis) {
		this.redis = redis;
	}

	@Override
	public boolean tryLock(String key, String owner, Duration ttl) {
		return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, owner, ttl));
	}

	@Override
	public void unlock(String key, String owner) {
		redis.execute(UNLOCK, List.of(key), owner);
	}
}
