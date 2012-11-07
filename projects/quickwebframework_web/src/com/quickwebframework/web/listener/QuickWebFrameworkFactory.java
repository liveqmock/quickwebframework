package com.quickwebframework.web.listener;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.servlet.Filter;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServlet;

import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.BundleListener;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;

import com.quickwebframework.web.servlet.PluginManageServlet;
import com.quickwebframework.web.servlet.PluginResourceDispatcherServlet;
import com.quickwebframework.web.servlet.PluginViewDispatcherServlet;
import com.quickwebframework.web.servlet.QwfServlet;
import com.quickwebframework.web.thread.BundleAutoManageThread;

public abstract class QuickWebFrameworkFactory {

	public final static String CONST_FRAMEWORK_BRIDGE_CLASS_NAME = "com.quickwebframework.bridge.FrameworkBridge";
	public final static String PLUGIN_CONFIG_FILES_PROPERTY_KEY = "quickwebframework.pluginConfigFiles.";

	// 配置文件路径参数名称
	public final static String CONFIG_LOCATION_PARAMETER_NAME = "quickwebframeworkConfigLocation";

	// QuickWebFramework的配置
	private static Properties quickWebFrameworkProperties;

	private static Framework framework;

	/**
	 * 得到Framework对象
	 * 
	 * @return
	 */
	public static Framework getFramework() {
		return framework;
	}

	/**
	 * 得到BundleContext对象
	 * 
	 * @param servletContext
	 * @return
	 */
	public static BundleContext getBundleContext() {
		return framework.getBundleContext();
	}

	private static Object frameworkBridgeObject;

	/**
	 * 得到框架桥接对象 此对象是HttpServlet,Filter,以及Servlet八大Listener的继承类或实现
	 * 
	 * @return
	 */
	public static Object getFrameworkBridgeObject() {
		return frameworkBridgeObject;
	}

	// 刷新框架桥接对象
	private static void refreshFrameworkBridgeObject() {
		ServiceReference<?> serviceReference = getBundleContext()
				.getServiceReference(CONST_FRAMEWORK_BRIDGE_CLASS_NAME);
		if (serviceReference == null)
			return;
		frameworkBridgeObject = getBundleContext().getService(serviceReference);
	}

	// 相应的Servlet
	public static List<QwfServlet> qwfServletList = new ArrayList<QwfServlet>();

	// 启动OSGi Freamwork
	public void startOSGiFreamwork(ServletContext servletContext) {
		String propertiesFileName = servletContext
				.getInitParameter(CONFIG_LOCATION_PARAMETER_NAME);

		if (propertiesFileName == null) {
			throw new RuntimeException(String.format(
					"Servlet参数[%s]未找到，QuickWebFramework启动失败！",
					CONFIG_LOCATION_PARAMETER_NAME));
		}

		String quickWebFrameworkPropertiesFilePath = servletContext
				.getRealPath(propertiesFileName);

		File quickWebFrameworkPropertiesFile = new File(
				quickWebFrameworkPropertiesFilePath);
		if (!quickWebFrameworkPropertiesFile.exists()
				|| !quickWebFrameworkPropertiesFile.isFile()) {
			throw new RuntimeException(String.format(
					"QuickWebFramework配置文件[%s]未找到！",
					quickWebFrameworkPropertiesFilePath));
		}

		// QuickWebFramework的配置
		quickWebFrameworkProperties = new Properties();
		try {
			InputStream quickWebFrameworkPropertiesInputStream = new FileInputStream(
					quickWebFrameworkPropertiesFilePath);
			Reader quickWebFrameworkPropertiesReader = new InputStreamReader(
					quickWebFrameworkPropertiesInputStream, "utf-8");
			quickWebFrameworkProperties.load(quickWebFrameworkPropertiesReader);
			quickWebFrameworkPropertiesReader.close();
			quickWebFrameworkPropertiesInputStream.close();
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}

		// ====================
		// 初始化OSGi框架
		// ====================
		String osgiFrameworkFactoryClass = quickWebFrameworkProperties
				.getProperty("quickwebframework.osgiFrameworkFactoryClass");
		Class<?> osgiFrameworkFactoryClazz;
		try {
			osgiFrameworkFactoryClazz = Class
					.forName(osgiFrameworkFactoryClass);
		} catch (ClassNotFoundException e) {
			throw new RuntimeException("查找osgiFrameworkFactoryClass类失败！", e);
		}
		// 如果osgiFrameworkFactoryClazz不是FrameworkFactory的派生类
		if (!FrameworkFactory.class.isAssignableFrom(osgiFrameworkFactoryClazz)) {
			throw new RuntimeException(
					"指定的osgiFrameworkFactoryClass不是org.osgi.framework.launch.FrameworkFactory的派生类！");
		}
		FrameworkFactory factory;
		try {
			factory = (FrameworkFactory) osgiFrameworkFactoryClazz
					.newInstance();
		} catch (Exception e) {
			throw new RuntimeException("初始化osgiFrameworkFactoryClass失败！", e);
		}
		// 配置Map
		Map<String, String> osgiFrameworkConfigMap = new HashMap<String, String>();

		// 配置缓存保存路径
		String osgiFrameworkStorage = quickWebFrameworkProperties
				.getProperty("quickwebframework.osgiFrameworkStorage");
		osgiFrameworkStorage = servletContext.getRealPath(osgiFrameworkStorage);
		if (osgiFrameworkStorage != null) {
			osgiFrameworkConfigMap.put("org.osgi.framework.storage",
					osgiFrameworkStorage);
		}

		// 读取固定配置
		String osgiFrameworkFactoryConfig = quickWebFrameworkProperties
				.getProperty("quickwebframework.osgiFrameworkFactoryConfig");
		if (osgiFrameworkFactoryConfig != null) {
			String[] configLines = osgiFrameworkFactoryConfig.split(";");
			for (String configLine : configLines) {
				String[] tmpArray = configLine.split("=");
				if (tmpArray.length >= 2) {
					String key = tmpArray[0].trim();
					String value = tmpArray[1].trim();
					osgiFrameworkConfigMap.put(key, value);
				}
			}
		}
		framework = factory.newFramework(osgiFrameworkConfigMap);

		try {
			// Framework初始化
			framework.init();

			QwfServlet tmpServlet = null;
			// 初始化插件视图Servlet
			tmpServlet = PluginViewDispatcherServlet.initServlet(
					servletContext, quickWebFrameworkProperties);
			if (tmpServlet != null) {
				qwfServletList.add(tmpServlet);
			}
			// 初始化插件资源Servlet
			tmpServlet = PluginResourceDispatcherServlet.initServlet(
					servletContext, quickWebFrameworkProperties);
			if (tmpServlet != null) {
				qwfServletList.add(tmpServlet);
			}
			// 初始化插件管理Servlet
			tmpServlet = PluginManageServlet.initServlet(servletContext,
					quickWebFrameworkProperties);
			if (tmpServlet != null) {
				qwfServletList.add(tmpServlet);
			}

			// 将ServletContext注册为服务
			getBundleContext().registerService(ServletContext.class.getName(),
					servletContext, null);

			// 设置插件要用到的配置文件
			Enumeration<?> quickWebFrameworkPropertieNameEnumeration = quickWebFrameworkProperties
					.propertyNames();
			while (quickWebFrameworkPropertieNameEnumeration.hasMoreElements()) {
				String propertieName = (String) quickWebFrameworkPropertieNameEnumeration
						.nextElement();
				if (propertieName.startsWith(PLUGIN_CONFIG_FILES_PROPERTY_KEY)) {
					String propName = propertieName
							.substring(PLUGIN_CONFIG_FILES_PROPERTY_KEY
									.length());
					String filePath = quickWebFrameworkProperties
							.getProperty(propertieName);
					Dictionary<String, String> dict = new Hashtable<String, String>();
					dict.put("quickwebframework.pluginConfigFile", propName);
					getBundleContext().registerService(String.class.getName(),
							servletContext.getRealPath(filePath), dict);
				}
			}
			// 设置WEB根目录到系统配置中
			System.setProperty("web.root.dir", servletContext.getRealPath("/"));

			// Bundle监听器
			getBundleContext().addBundleListener(new BundleListener() {
				public void bundleChanged(BundleEvent arg0) {
				}
			});
			// Service监听器
			getBundleContext().addServiceListener(new ServiceListener() {
				public void serviceChanged(ServiceEvent arg0) {
					// 如果是DispatcherServlet服务更改
					if (arg0.getServiceReference().toString()
							.contains(CONST_FRAMEWORK_BRIDGE_CLASS_NAME)) {
						refreshFrameworkBridgeObject();
					}
				}
			});

			framework.start();
			System.out.println("启动OSGi Framework成功！");

			// 扫描插件目录，看是否有插件需要自动安装
			Thread trdBundleAutoManage = new BundleAutoManageThread(
					osgiFrameworkStorage);
			trdBundleAutoManage.start();
		} catch (BundleException e) {
			throw new RuntimeException("启动OSGi Framework失败！", e);
		}
	}

	/**
	 * 停止OSGi框架
	 */
	public void stopOSGiFramework() {
		try {
			if (framework != null)
				framework.stop();
			System.out.println("停止OSGi Framework成功！");
		} catch (BundleException e) {
			throw new RuntimeException("停止OSGi Framework失败！", e);
		}
	}
}