package top.thinkin.lightd.benchmark;

import cn.hutool.core.collection.CollectionUtil;
import org.rocksdb.RocksDB;
import top.thinkin.lightd.collect.DB;
import top.thinkin.lightd.collect.RList;
import top.thinkin.lightd.collect.Sequence;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Benchmark {
    static int availProcessors = Runtime.getRuntime().availableProcessors();
    static ExecutorService executorService = Executors.newFixedThreadPool(availProcessors);

    public static void main(String[] args) throws Exception {
        RocksDB.loadLibrary();
        DB db = DB.build("D:\\temp\\db");
        for (int i = 0; i < 100000; i++) {
            bc(db);
        }


        db.clear();
        db.close();
        executorService.shutdown();
    }

    private static void bc(DB db) throws Exception {


        retry("INCR", 100, w_times -> {
            Sequence sequence = db.getSequence("test");
            for (int i = 0; i < w_times * (10000); i++) {
                sequence.incr(1L);
            }
        });

        RList LPUSHs = db.getList("LPUSH_LIST");
        retry("LPUSH", 100, w_times -> add(LPUSHs, w_times));
        LPUSHs.delete();

        RList LPOP_LIST = db.getList("LPOP_LIST");
        addAll(LPOP_LIST, 100);
        retry("LPOP", 100, w_times -> blpop(LPOP_LIST));
        LPOP_LIST.delete();

        RList LRANGE_500 = db.getList("LRANGE_500_LIST");
        addAll(LRANGE_500, 100);
        retry("LRANGE_500", 100, 100000, w_times -> range(LRANGE_500, 100000));
        LRANGE_500.delete();

    }

    private static void range(RList list, int size) {
        JoinFuture<String> joinFuture = JoinFuture.build(executorService, String.class);
        for (int j = 0; j < availProcessors; j++) {
            joinFuture.add(args -> {
                for (int i = 0; i < size / availProcessors; i++) {
                    try {
                        list.range(0, 450);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                return "";
            });
        }
        joinFuture.join();
    }


    private static void blpop(RList list) throws Exception {
        while (true) {
            java.util.List<byte[]> pops = list.blpop(1);
            if (CollectionUtil.isEmpty(pops)) break;
        }
    }

    private static void add(RList list, int w_times) throws Exception {
        int k = w_times * 10000;
        List<byte[]> arrayList = new ArrayList<>(w_times * 10000);
        for (int i = 0; i < k; i++) {
            arrayList.add((i + "t").getBytes());
        }
        for (byte[] bytes : arrayList) {
            list.add(bytes);
        }
    }

    private static void addAll(RList list, int w_times) throws Exception {

        int k = w_times * 10000;
        List<byte[]> arrayList = new ArrayList<>();
        for (int i = 0; i < k; i++) {
            arrayList.add((i + "t").getBytes());
        }

        FList<byte[]> fList = new FList(arrayList);
        List<List<byte[]>> lists = fList.split(10000);
        for (List<byte[]> bytes : lists) {
            list.addAll(bytes);
        }
    }


    public static void retry(String name, int w_times, Function function) throws Exception {
        long startTime = System.currentTimeMillis(); //获取开始时间
        function.call(w_times);
        long endTime = System.currentTimeMillis(); //获取结束时间
        System.out.println("benchmark " + name + ":" + ((w_times * 10000.0) / (endTime - startTime)) * 1000 + " per second"); //输出程序运行时间
    }


    public static void retry(String name, int w_times, int size, Function function) throws Exception {
        long startTime = System.currentTimeMillis(); //获取开始时间
        function.call(w_times);
        long endTime = System.currentTimeMillis(); //获取结束时间
        System.out.println("benchmark " + name + ":" + ((size * 1.0 / (endTime - startTime)) * 1000 + " per second")); //输出程序运行时间
    }

    public interface Function {
        void call(int w_times) throws Exception;
    }

}
