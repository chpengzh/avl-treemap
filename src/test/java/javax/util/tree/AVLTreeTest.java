package javax.util.tree;

import org.junit.*;
import org.junit.runners.MethodSorters;

import java.util.*;
import java.util.logging.Logger;

@FixMethodOrder(value = MethodSorters.NAME_ASCENDING)
public class AVLTreeTest {

    private static final Logger LOG = Logger.getLogger(AVLTreeTest.class.getName());

    private final AVLTreeMap<Long, Long> map = new AVLTreeMap<>();
    private final Random rand = new Random(System.currentTimeMillis());

    @Before
    public void before() {
        Assert.assertTrue(map.isEmpty());
        Assert.assertTrue(map.isBalance());
    }

    @After
    public void after() {
        Assert.assertTrue(map.isBalance());
        map.clear();
    }

    @Test
    public void t01Empty() {
        LOG.info("=> Empty test, check if state is true while starting");
        Assert.assertTrue(map.isEmpty());
        Assert.assertTrue(map.height() == 0);
        Assert.assertTrue(map.size() == 0);
    }

    @Test
    public void t02OrderedInsert() {
        LOG.info("=> Ordered insert 1M items(from 0 to 1M-1). check if state is true after insert");
        for (long i = 0; i < 1024L * 1024L; i++) {
            map.put(i, i);
        }
        Assert.assertTrue(!map.isEmpty());
        Assert.assertTrue(map.height() == 21);
        Assert.assertTrue(map.size() == 1024 * 1024);
    }

    @Test
    public void t03AntiOrderedInsert() {
        LOG.info("=> Ordered insert 1M items(from 1M to 1). check if state is true after insert");
        for (long i = 1024L * 1024L; i > 0; i--) {
            map.put(i, i);
        }
        Assert.assertTrue(!map.isEmpty());
        Assert.assertTrue(map.height() == 21);
        Assert.assertTrue(map.size() == 1024 * 1024);
    }

    @Test
    public void t04RandomInsert() {
        LOG.info("=> Random insert 1M items(from 1M to 1). check if state is true after insert");
        for (long i = 0; i < 1024L * 1024L; i++) {
            map.put(rand.nextLong(), rand.nextLong());
        }
    }

    @Test
    public void t05OrderedRemove() {
        LOG.info("=> Random insert 1M items(from 1M to 1). check if state is true after insert");
        Set<Long> sample = new TreeSet<>();
        for (long i = 0; i < 1024L * 1024L; i++) {
            long next = rand.nextLong();
            map.put(next, next);
            sample.add(next);
        }
        for (Long next : sample) {
            Assert.assertEquals(next, map.remove(next));
        }
    }

    @Test
    public void t06AntiOrderedRemove() {
        Set<Long> sample = new TreeSet<>();
        for (long i = 1024L * 1024L; i > 0; i--) {
            long next = rand.nextLong();
            map.put(next, next);
            sample.add(next);
        }
        for (Long next : sample) {
            Assert.assertEquals(next, map.remove(next));
        }
    }

    @Test
    public void t07RandomRemove() {
        Set<Long> contains = new HashSet<>();
        for (long i = 0; i < 256L * 256L; i++) {
            long next = rand.nextLong();
            map.put(next, next);
            contains.add(next);
        }
        for (int i = 0; i < 512L * 512L; i++) {
            Long next = rand.nextLong();
            Long value = map.remove(next);
            Assert.assertEquals(contains.remove(next) ? next : null, value);
        }
    }

    @Test
    public void t08SizeTest() {
        Set<Long> contains = new HashSet<>();
        for (long i = 0; i < 1024L * 1024L; i++) {
            long next = rand.nextLong();
            map.put(next, next);
            contains.add(next);
            if (i % 1000L == 0) continue;

            Assert.assertEquals(map.size(), contains.size());
            long min = (long) (Math.pow((1 + Math.sqrt(5)) / 2, map.height() + 1) / Math.sqrt(5) - 1);//min size
            long max = (long) (Math.pow(2, map.height()));//max size
            Assert.assertTrue(map.size() >= min);
            Assert.assertTrue(map.size() <= max);
        }
    }

    @Test
    public void t09TopTest() {
        Map<Long, Long> contains = new HashMap<>();
        for (long i = 0; i < 1024L * 1024L; i++) {
            long next = rand.nextLong();
            map.put(next, next);
            contains.put(next, next);
        }
        int offset = rand.nextInt(10);
        int limit = rand.nextInt(10);
        List<Long> keys = new ArrayList<>(contains.keySet());
        keys.sort(Comparator.reverseOrder());
        Iterator<Map.Entry<Long, Long>> top = map.max(offset, limit).entrySet().iterator();
        for (int i = offset; i < offset + limit && i < keys.size(); i++) {
            Long key = contains.get(keys.get(i));
            Map.Entry<Long, Long> result = top.next();
            Assert.assertEquals(key, result.getKey());
            Assert.assertEquals(contains.get(key), result.getValue());
        }
        Assert.assertTrue(!top.hasNext());
    }

}
