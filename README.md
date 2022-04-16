# EventBus 源码解析

## Todo

目前的计划是对最新的3.3.1版本进行源码解析，项目中目前有对3.2.0版本的源码解析，但是该部分的源码解析是我在上学时候做的，可能有些地方不准确、认识不到位，所以有了本次的对新版本的源码再次解析，一是同步最新代码，二是回顾解析，发现以前没有发现的亮点，纠正以前不准确的注释。

## 信息

项目名称：EventBus

当前版本：3.3.1

项目地址：https://github.com/greenrobot/EventBus

所属组织：greenrobot 

官网：https://greenrobot.org/eventbus/

## 项目结构

从 3.3.0 版本开始，**greenrobot** 将 **EventBus** 分离为 **Java** 和 **Android** 两部分，并且默认情况下引入的依赖为 **Android** 版本，**Java** 版本需要调整依赖地址。**Android** 部分是我们需要关注的。

对于 **Android** 部分，我们需要关心的有三个模块：

- **EventBus**

  该模块是 EventBus 的核心内容

- **eventbus-android**

  该模块是针对 Android 平台特性的支持

- **EventBusAnnotationProcessor**

  针对 Subscribe 注解的注解处理器

