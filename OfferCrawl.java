/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package offercrawl;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 *
 * @author mz
 */
public class OfferCrawl {

    ArrayList<String> allurlSet = new ArrayList<String>();//所有的网页url，需要更高效的去重可以考虑HashSet
    ArrayList<String> notCrawlurlSet = new ArrayList<String>();//未爬过的网页url
    HashMap<String, Integer> depth = new HashMap<String, Integer>();//所有网页的url深度
    static Jedis pool=new Jedis("localhost");
    int crawDepth = 5; //爬虫深度
    int threadCount = 10; //线程数量
    int count = 0; //表示有多少个线程处于wait状态
    public static final Object signal = new Object();   //线程间通信变量

    public static void main(String[] args) {
        final OfferCrawl wc = new OfferCrawl();
//		wc.addUrl("http://www.126.com", 1);
        wc.addUrl("http://701b.taisha.org/app/offer", 1);
        long start = System.currentTimeMillis();
       
        
        System.out.println("开始爬虫.........................................");
        wc.begin();

        while (true) {
            if (pool.lpop("0")==null && Thread.activeCount() == 1 || wc.count == wc.threadCount) {
                long end = System.currentTimeMillis();
                System.out.println("总共爬了" + wc.allurlSet.size() + "个网页");
                System.out.println("总共耗时" + (end - start) / 1000 + "秒");
                System.exit(1);
//				break;
            }

        }
    }

    private void begin() {
        for (int i = 0; i < threadCount; i++) {
            new Thread(new Runnable() {
                public void run() {
//					System.out.println("当前进入"+Thread.currentThread().getName());
//					while(!notCrawlurlSet.isEmpty()){ ----------------------------------（1）
//						String tmp = getAUrl();
//						crawler(tmp);
//					}
                    while (true) {
//						System.out.println("当前进入"+Thread.currentThread().getName());
                        String tmp = getAUrl();
                        if (tmp != null) {
                            crawler(tmp);
                        } else {
                            synchronized (signal) {  //------------------（2）
                                try {
                                    count++;
                                    System.out.println("当前有" + count + "个线程在等待");
                                    signal.wait();
                                } catch (InterruptedException e) {
                                    // TODO Auto-generated catch block
                                    e.printStackTrace();
                                }
                            }

                        }
                    }
                }
            }, "thread-" + i).start();
        }
    }

    public synchronized String getAUrl() {
        if (notCrawlurlSet.isEmpty()) {
            return null;
        }
        String tmpAUrl;
//		synchronized(notCrawlurlSet){
        tmpAUrl = notCrawlurlSet.get(0);
        notCrawlurlSet.remove(0);
//		}
        
         tmpAUrl=pool.lpop("0");
         pool.sadd("1",tmpAUrl);
        return tmpAUrl;
    }
//	public synchronized  boolean isEmpty() {
//		boolean f = notCrawlurlSet.isEmpty();
//		return f;
//	}

    public synchronized void addUrl(String url, int d) {
        notCrawlurlSet.add(url);
        allurlSet.add(url);
     
             pool.rpush("0", url);
        
       
        
        depth.put(url, d);
    }

    //爬网页sUrl
    public void crawler(String sUrl) {
        URL url;
        try {
            url = new URL(sUrl);
//				HttpURLConnection urlconnection = (HttpURLConnection)url.openConnection(); 
            URLConnection urlconnection = url.openConnection();
            urlconnection.addRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.0)");

            InputStream is = url.openStream();
            BufferedReader bReader = new BufferedReader(new InputStreamReader(is));
            StringBuffer sb = new StringBuffer();//sb为爬到的网页内容  
            String rLine = null;
            while ((rLine = bReader.readLine()) != null) {
                sb.append(rLine);
                sb.append("/r/n");
            }
            int d = depth.get(sUrl);
            System.out.println("爬网页" + sUrl + "成功，深度为" + d + " 是由线程" + Thread.currentThread().getName() + "来爬");
            if (d < crawDepth) {
                //解析网页内容，从中提取链接
                parseContext(sb.toString(), d + 1);

            }
//				System.out.println(sb.toString());

        } catch (IOException e) {
//			crawlurlSet.add(sUrl);
//			notCrawlurlSet.remove(sUrl);
            e.printStackTrace();
        }
    }

    //从context提取url地址
    public void parseContext(String context, int dep) throws FileNotFoundException {


        Document doc = Jsoup.parse(context);
        Elements allPages = doc.getAllElements();
        Elements otherPages=new Elements();
        Elements details=new Elements();
        PrintWriter out = new PrintWriter("D:\\out.txt");
        for (Element all : allPages) {

            out.println("classname" + all.className());
            out.println("cs" + all.cssSelector());
            out.println("html" + all.html());
            out.println("id" + all.id());
            out.println("tagname" + all.tagName());
if(all.className().equals("offer_ph_contry left")||all.className().equals("offer_page right")){
    otherPages.add(all);
}
if(all.className().equals("bao_offer_xx_xuan")){
    details.add(all);
}
            out.flush();

        }
        out.close();
        
        


        PrintWriter out2 = new PrintWriter("D:\\out2.txt");
        for (Element otherPage : otherPages) {
            Elements Pages = otherPage.children();
            
            for (Element Page : Pages) {
                out2.println("classname" + Page.className());
                out2.println("cs" + Page.cssSelector());
                out2.println("html" + Page.html());
                out2.println("id" + Page.id());
                out2.println("tagname" + Page.tagName());

                out2.flush();
                String url = Page.attr("href");
                System.out.println("jsoupurl" + url);
                String absUrl="http://701b.taisha.org"+url;
                 System.out.println("jsoupabsurl" + absUrl);
                addUrl(absUrl, dep);//加入一个新的url
                if (count > 0) { //如果有等待的线程，则唤醒
                    synchronized (signal) {  //---------------------（2）
                        count--;
                        signal.notify();
                    }
                }
            }
        }
        out2.close();


    }
}
