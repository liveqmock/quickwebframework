package com.quickwebframework.framework;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;

import com.quickwebframework.entity.Log;
import com.quickwebframework.entity.LogFactory;

public abstract class ThreadContext {
	private static Log log = LogFactory.getLog(ThreadContext.class);

	public static void init() {
		final Bundle coreBundle = FrameworkContext.coreBundle;
		coreBundle.getBundleContext().addBundleListener(new BundleListener() {
			@Override
			public void bundleChanged(BundleEvent arg0) {
				int eventType = arg0.getType();
				Bundle bundle = arg0.getBundle();

				// 如果插件的状态是正在停止或已经停止
				if (eventType == BundleEvent.STOPPED
						|| eventType == BundleEvent.STOPPING) {
					if (bundle.equals(coreBundle)) {
						removeAllFilter();
					} else {
						removeBundleAllThread(bundle);
					}
				}
			}
		});
	}

	private static List<Thread> threadList = new ArrayList<Thread>();
	private static Map<Bundle, List<Thread>> bundleThreadListMap = new HashMap<Bundle, List<Thread>>();

	/**
	 * 得到线程列表
	 * 
	 * @return
	 */
	public static List<Thread> getThreadList() {
		return threadList;
	}

	/**
	 * 移除所有的线程
	 */
	public static void removeAllFilter() {
		for (Bundle bundle : bundleThreadListMap.keySet()
				.toArray(new Bundle[0])) {
			removeBundleAllThread(bundle);
		}
	}

	/**
	 * 移除某Bundle所有的线程
	 * 
	 * @param bundle
	 */
	public static void removeBundleAllThread(Bundle bundle) {
		if (!bundleThreadListMap.containsKey(bundle))
			return;
		Thread[] bundleThreadArray = bundleThreadListMap.get(bundle).toArray(
				new Thread[0]);

		for (Thread thread : bundleThreadArray) {
			removeThread(bundle, thread);
		}
		bundleThreadListMap.remove(bundle);
	}

	/**
	 * 移除线程
	 * 
	 * @param bundle
	 * @param thread
	 */
	public static void removeThread(Bundle bundle, Thread thread) {

		// 从Bundle对应的线程列表中移除
		if (!bundleThreadListMap.containsKey(bundle))
			return;
		List<Thread> bundleThreadList = bundleThreadListMap.get(bundle);
		bundleThreadList.remove(thread);

		// 从所有的线程列表中移除
		threadList.remove(thread);

		String bundleName = bundle.getSymbolicName();

		String threadName = String.format(
				"[Thread Id:%s ,Name:%s ,Class:%s ,Hashcode:%s]",
				thread.getId(), thread.getName(), thread.getClass().getName(),
				Integer.toHexString(thread.hashCode()));
		try {
			thread.interrupt();
			log.info(String.format("已成功向插件[%s]的线程[%s]发送中断命令！", bundleName,
					threadName));
		} catch (Exception ex) {
			log.error(String.format("向插件[%s]的线程[%s]发送中断命令失败！", bundleName,
					threadName));
			ex.printStackTrace();
		}
	}

	/**
	 * 添加线程
	 * 
	 * @param bundle
	 * @param thread
	 */
	public static void addThread(Bundle bundle, Thread thread) {
		// 加入到Bundle对应的线程列表中
		List<Thread> bundleThreadList = null;
		if (bundleThreadListMap.containsKey(bundle)) {
			bundleThreadList = bundleThreadListMap.get(bundle);
		} else {
			bundleThreadList = new ArrayList<Thread>();
			bundleThreadListMap.put(bundle, bundleThreadList);
		}
		bundleThreadList.add(thread);

		// 加入到全部线程列表中
		threadList.add(thread);

		// 启动线程
		try {
			thread.start();
			log.info(String.format("已成功启动插件[%s]的线程[%s]！",
					bundle.getSymbolicName(), thread));
		} catch (Exception ex) {
			log.error(String.format("启动插件[%s]的线程[%s]失败！",
					bundle.getSymbolicName(), thread));
		}
	}
}