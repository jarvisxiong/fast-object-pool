
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;

import java.text.DecimalFormat;
import java.util.concurrent.CountDownLatch;

/**
 * @author Daniel
 */
public class BenchmarkCommons {

    static double[] statsAvgRespTime;
    static double[] statsAvgBorrow;
    static double[] statsAvgReturn;


    public BenchmarkCommons(int workerCount, int loop) throws Exception {
        CountDownLatch latch = new CountDownLatch(workerCount);
        statsAvgRespTime = new double[workerCount];
        statsAvgBorrow = new double[workerCount];
        statsAvgReturn = new double[workerCount];

        GenericObjectPool pool = new GenericObjectPool(new PooledObjectFactory() {
            @Override
            public PooledObject makeObject() throws Exception {
                return new DefaultPooledObject(new StringBuilder());
            }

            @Override
            public void destroyObject(PooledObject pooledObject) throws Exception {

            }

            @Override
            public boolean validateObject(PooledObject pooledObject) {
                return false;
            }

            @Override
            public void activateObject(PooledObject pooledObject) throws Exception {

            }

            @Override
            public void passivateObject(PooledObject pooledObject) throws Exception {

            }
        });
        pool.setMinIdle(25);
        pool.setMaxIdle(50);
        pool.setMaxTotal(50);
        pool.setMinEvictableIdleTimeMillis(60 * 1000 * 5L);

        Worker[] workers = new Worker[workerCount];
        for (int i = 0; i < workerCount; i++) {
            workers[i] = new Worker(i, pool, latch, loop);
        }
        long t1 = System.currentTimeMillis();
        for (int i = 0; i < workerCount; i++) {
            workers[i].start();
        }
        latch.await();
        long t2 = System.currentTimeMillis();
        double stats = 0;
        for (int i = 0; i < workerCount; i++) {
            stats += statsAvgRespTime[i];
        }
        System.out.println("Average Response Time:" + new DecimalFormat("0.00").format(stats / workerCount));
        stats = 0;
        for (int i = 0; i < workerCount; i++) {
            stats += statsAvgBorrow[i];
        }
        System.out.println("Average Borrow Time:" + new DecimalFormat("0.00").format(stats / workerCount));
        stats = 0;
        for (int i = 0; i < workerCount; i++) {
            stats += statsAvgReturn[i];
        }
        System.out.println("Average Return Time:" + new DecimalFormat("0.00").format(stats / workerCount));
        System.out.println("Average Througput Per Second:" + new DecimalFormat("0").format(( (double) loop * workerCount * 1000 ) / (t2 - t1) ));
    }

    private static class Worker extends Thread {

        private final int id;
        private final GenericObjectPool pool;
        private final CountDownLatch latch;
        private final int loop;

        public Worker(int id, GenericObjectPool pool, CountDownLatch latch, int loop) {
            this.id = id;
            this.pool = pool;
            this.latch = latch;
            this.loop = loop;
        }

        @Override public void run() {
            long t1 = System.currentTimeMillis();
            long tb = 0, tr = 0;
            for (int i = 0; i < loop; i++) {
                StringBuilder obj = null;
                try {
                    long tp1 = System.currentTimeMillis();
                    obj = (StringBuilder) pool.borrowObject();
                    tb += System.currentTimeMillis() - tp1;
                    obj.append("x");
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (obj != null) {
                        try {
                            long tp3 = System.currentTimeMillis();
                            pool.returnObject(obj);
                            tr += System.currentTimeMillis() - tp3;
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
            long t2 = System.currentTimeMillis();
            latch.countDown();
            synchronized (statsAvgRespTime) {
                statsAvgRespTime[id] =  ((double) (t2 - t1)) / loop;
                statsAvgBorrow[id] =  ((double) tb) / loop;
                statsAvgReturn[id] =  ((double) tr) / loop;
            }
        }
    }
}
