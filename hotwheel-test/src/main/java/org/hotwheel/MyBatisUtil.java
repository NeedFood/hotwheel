package org.hotwheel;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.hotwheel.dao.dsmp.IOverdueMessageDao;
import org.hotwheel.dao.ermas.IOverdueErrorDao;
import org.hotwheel.ibatis.builder.ApplicationContext;

import java.io.IOException;
import java.io.Reader;
import java.util.List;

/**
 * Created by wangfeng on 2016/11/1.
 * @since 1.0
 */
public class MyBatisUtil {
    private final static String projectPath = "/Users/wangfeng/projects/mymmsc/hotwheel/hotwheel-test";
    private final static String resourcePath = projectPath + "/src/main/resources";
    private final static String xmlFilename = "applicationContext-orm.xml";
    private final static String xmlMybatisConfig = resourcePath +  "/profiles/local/" + xmlFilename;
    // 每一个MyBatis的应用程序都以一个SqlSessionFactory对象的实例为核心
    // 使用SqlSessionFactory的最佳实践是在应用运行期间不要重复创建多次,最佳范围是应用范围
    private static SqlSessionFactory sqlSessionFactory;
    private static ApplicationContext applicationContext;

    private static void loadResource() {
        String resource = xmlMybatisConfig;
        Reader reader = null;
        try {
            reader = Resources.getResourceAsReader(resource);
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
        // SqlSessionFactory对象的实例可以通过SqlSessionFactoryBuilder对象来获得
        // SqlSessionFactoryBuilder实例的最佳范围是方法范围（也就是本地方法变量）。
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(reader);
    }

    private static void loadFile() {
        try {
            applicationContext = new ApplicationContext("classpath:/" + xmlFilename);
            applicationContext.parse();
            //Configuration config = builder.parse();
            //sqlSessionFactory = new SqlSessionFactoryBuilder().build(config);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static SqlSessionFactory getSqlSessionFactory() {
        loadFile();
        return sqlSessionFactory;
    }

    public static void main(String[] args) {
        // 获得SqlSession的实例
        // 每个线程都应该有它自己的SqlSession实例。SqlSession的实例不能被共享，也是线程不安全的。因此最佳的范围是请求或方法范围。
        loadFile();
        SqlSession sqlSession = null;
        Class<?> clazz = null;
        try {
            clazz = IOverdueErrorDao.class;
            sqlSession = applicationContext.getSesseion(clazz);
            IOverdueErrorDao userMapper = (IOverdueErrorDao) sqlSession.getMapper(clazz);
            List<String> listError = userMapper.getAllDirtyAndErrorData();
            //sqlSession.commit();
            if(listError != null) {
                for (String uuid : listError) {
                    System.out.println("cuishou:" + uuid);
                }
            } else {
                System.out.println("cuishou:" +"null");
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            sqlSession.close();
        }

        try {
            clazz = IOverdueMessageDao.class;
            sqlSession = applicationContext.getSesseion(clazz);
            IOverdueMessageDao overdueMessage = (IOverdueMessageDao) sqlSession.getMapper(clazz);
            int count = overdueMessage.countMessage();
            System.out.println("dsmp-count:" + count);

        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            sqlSession.close();
        }
    }
}