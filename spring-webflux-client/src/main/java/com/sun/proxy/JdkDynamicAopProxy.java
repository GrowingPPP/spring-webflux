package com.sun.proxy;

import com.sun.annotation.APIServer;
import com.sun.beans.MethodInfo;
import com.sun.beans.ServerInfo;
import com.sun.client.RestHandler;
import com.sun.client.WebClientRestHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @description: jdk动态代理类
 * @author: 星际一哥
 * @create: 2019-12-17 19:43
 */
@Slf4j
public class JdkDynamicAopProxy implements ProxyCreator {

    RestHandler restHandler = new WebClientRestHandler();

    @Override
    public Object createProxy(Class<?> type) {
        ServerInfo serverInfo = extractServerInfo(type);
        log.info("serverInfo :{}", serverInfo);

        // 初始化服务器信息（webClient）
        restHandler.init(serverInfo);

        return Proxy.newProxyInstance(this.getClass().getClassLoader(), new Class[]{type}, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

                // 抽取所有请求信息封装到 MethodInfo 对象
                MethodInfo methodInfo = extractMethodInfo(method, args);
                log.info("methodInfo :{}", methodInfo);

                // 调用Rest客户端 处理请求
                return restHandler.invokeRest(methodInfo);
            }

            private MethodInfo extractMethodInfo(Method method, Object[] args) {

                MethodInfo methodInfo = new MethodInfo();

                // 封装url和请求方式
                extractRequsetUrlAndMethod(method, methodInfo);

                // 封装请求参数和body
                extractRequestParamsAndBody(method, args, methodInfo);

                return methodInfo;
            }

            /**
             * 抽取请求参数和请求body
             * @param method
             * @param args
             * @param methodInfo
             */
            private void extractRequestParamsAndBody(Method method, Object[] args, MethodInfo methodInfo) {
                Map<String, Object> params = new LinkedHashMap<>();
                Parameter[] parameters = method.getParameters();
                for (int i = 0; i < parameters.length; i++) {
                    PathVariable annotation = parameters[i].getAnnotation(PathVariable.class);
                    if (annotation != null) {
                        params.put(annotation.value(), args[i]);
                        methodInfo.setParams(params);
                    }

                    RequestBody requestBody = parameters[i].getAnnotation(RequestBody.class);
                    if (requestBody != null) {
                        methodInfo.setBody((Mono<?>) args[i]);
                    }
                }
            }

            /**
             * 抽取请求url 和请求方式
             * @param method
             * @param methodInfo
             */
            private void extractRequsetUrlAndMethod(Method method, MethodInfo methodInfo) {
                Annotation[] annotations = method.getAnnotations();
                Arrays.stream(annotations).forEach(annotation -> {
                    if (annotation instanceof GetMapping) {
                        GetMapping getMethod = (GetMapping) annotation;
                        methodInfo.setUrl(getMethod.value()[0]);
                        methodInfo.setHttpMethod(HttpMethod.GET);
                    } else if (annotation instanceof PostMapping) {
                        PostMapping postMethod = (PostMapping) annotation;
                        methodInfo.setUrl(postMethod.value()[0]);
                        methodInfo.setHttpMethod(HttpMethod.POST);
                    } else if (annotation instanceof DeleteMapping) {
                        DeleteMapping deleteMethod = (DeleteMapping) annotation;
                        methodInfo.setUrl(deleteMethod.value()[0]);
                        methodInfo.setHttpMethod(HttpMethod.DELETE);
                    } else if (annotation instanceof PutMapping) {
                        PutMapping putMethod = (PutMapping) annotation;
                        methodInfo.setUrl(putMethod.value()[0]);
                        methodInfo.setHttpMethod(HttpMethod.PUT);
                    }
                });
            }
        });

    }

    /**
     * 封装服务器信息
     *
     * @param type
     * @return
     */
    private ServerInfo extractServerInfo(Class<?> type) {
        String url = type.getAnnotation(APIServer.class).value();
        return ServerInfo.builder().url(url).build();
    }
}
