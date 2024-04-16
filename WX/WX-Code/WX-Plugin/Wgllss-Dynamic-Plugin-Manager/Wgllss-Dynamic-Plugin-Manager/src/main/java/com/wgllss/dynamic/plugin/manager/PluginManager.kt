package com.wgllss.dynamic.plugin.manager

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.IBinder
import android.text.TextUtils
import com.wgllss.core.activity.BaseViewPluginResActivity
import com.wgllss.core.activity.WActivityManager
import com.wgllss.core.units.WLog
import com.wgllss.dynamic.host.lib.classloader.PluginKey
import com.wgllss.dynamic.host.lib.constant.DynamicPluginConstant.RESOURCE_SKIN
import com.wgllss.dynamic.host.lib.constant.DynamicPluginConstant.WEB_ASSETS
import com.wgllss.dynamic.host.lib.constant.DynamicPluginConstant.dldir
import com.wgllss.dynamic.host.lib.constant.DynamicPluginConstant.versionFile
import com.wgllss.dynamic.host.lib.impl.WXDynamicLoader
import com.wgllss.dynamic.host.lib.loader_base.DynamicManageUtils
import com.wgllss.dynamic.host.lib.loader_base.DynamicManageUtils.getDlfn
import com.wgllss.dynamic.runtime.library.WXDynamicAidlInterface
import com.wgllss.sample.feature_system.savestatus.MMKVHelp
import java.io.File
import java.util.concurrent.ConcurrentHashMap


class PluginManager private constructor() {

    /** res apk*/
    private val resFileName by lazy {
        WXDynamicLoader.instance.loader.getMapDluImpl()[RESOURCE_SKIN]!!.run {
            getDlfn(first, second)
        }
    }

    private val webResFileName by lazy {
        WXDynamicLoader.instance.loader.getMapDluImpl()[WEB_ASSETS]!!.run {
            getDlfn(first, second)
        }
    }

    /** 加载插件管理器 dex **/
    private val clmd by lazy { WXDynamicLoader.instance.loader.getClmdImpl() }

    /** 下载接口实现 dex **/
    private val cdlfd by lazy { WXDynamicLoader.instance.loader.getCdlfdImpl() }

    /**others dex*/
    private val cotd by lazy { WXDynamicLoader.instance.loader.getCotdImpl() }

    private val context by lazy { WXDynamicLoader.instance.context }
    private val skinMap by lazy { ConcurrentHashMap<String, Resources>() }

    private val mapAidl by lazy { HashMap<String, WXDynamicAidlInterface>() }

    companion object {
        const val pluginApkPathKey = "PLUGIN_APK_PATH_KEY"
        const val activityNameKey = "ACTIVITY_NAME_KEY"
        const val serviceNameKey = "PLUGIN_SERVICE_NAME_KEY"
        const val privatePackageKey = "PRIVATE_PACKAGE_KEY"

        private const val PluginStandardActivity = "com.wgllss.dynamic.plugin.runtime.PluginStandardActivity"
        private const val PluginSingleInstanceActivity = "com.wgllss.dynamic.plugin.runtime.PluginSingleInstanceActivity"
        private const val PluginSingleTaskActivity = "com.wgllss.dynamic.plugin.runtime.PluginSingleTaskActivity"
        private const val PluginSingleTopActivity = "com.wgllss.dynamic.plugin.runtime.PluginSingleTopActivity"

        private const val PluginStartStickyService = "com.wgllss.dynamic.plugin.runtime.PluginStartStickyService"
        private const val PluginStartNotStickyService = "com.wgllss.dynamic.plugin.runtime.PluginStartNotStickyService"
        private const val PluginStartRedeliverIntentService = "com.wgllss.dynamic.plugin.runtime.PluginStartRedeliverIntentService"
        private const val PluginStartStickyCompatibilityService = "com.wgllss.dynamic.plugin.runtime.PluginStartStickyCompatibilityService"

        private const val PluginProcessStartStickyService = "com.wgllss.dynamic.plugin.runtime.PluginProcessStartStickyService"
        private const val PluginProcessStartNotStickyService = "com.wgllss.dynamic.plugin.runtime.PluginProcessStartNotStickyService"
        private const val PluginProcessStartRedeliverIntentService = "com.wgllss.dynamic.plugin.runtime.PluginProcessStartRedeliverIntentService"
        private const val PluginProcessStartStickyCompatibilityService = "com.wgllss.dynamic.plugin.runtime.PluginProcessStartStickyCompatibilityService"


        val instance by lazy { PluginManager() }
    }

    fun switchSkinResources(skinPath: String) {
        val file = File(skinPath)
        if (file.exists()) {
            val key = file.absolutePath
            if (!skinMap.containsKey(key)) {
                val res = getResourcesForApplication(file)
                skinMap[key] = res
            }
            MMKVHelp.saveSkinPath(key)
        }
    }

    fun callAllActivity(skinRes: Resources) {
        WActivityManager.getActivitys {
            if (it is BaseViewPluginResActivity) {
                it.callChangeSkin(skinRes)
            }
        }
    }

    fun getCurrentSkinPath() = MMKVHelp.getSkinPath() ?: getDefaultSkinPath()

    fun getPluginSkinResources(): Resources {
        val skinPath = MMKVHelp.getSkinPath()
        var file: File
        if (!TextUtils.isEmpty(skinPath)) {
            file = File(skinPath)
            if (!file.exists()) {
                file = File(getDefaultSkinPath())
            }
        } else {
            file = File(getDefaultSkinPath())
        }
        val key = file.absolutePath
        return if (skinMap.containsKey(key)) {
            skinMap[key]!!
        } else {
            android.util.Log.e("PluginManager", "PluginManager :${file.absolutePath}")
            val res = getResourcesForApplication(file)
            skinMap[key] = res
            MMKVHelp.saveSkinPath(key)
            res
        }
    }

    fun getPluginResources(contentKey: String): Resources {
        return getResourcesForApplication(DynamicManageUtils.getDxFile(context, dldir, getDlfn(contentKey, cotd[contentKey]!!)))
    }

    private fun getDefaultSkinPath() = StringBuilder(context.filesDir.absolutePath).apply {
        append(File.separator)
        append(dldir)
        append(File.separator)
        append(resFileName)
    }.toString()

    /**
     * web res 资源
     */
    fun getWebRes(): Resources {
        val file = DynamicManageUtils.getDxFile(context, dldir, webResFileName)
        val flags = (PackageManager.GET_META_DATA or PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES
                or PackageManager.GET_PROVIDERS or PackageManager.GET_RECEIVERS)
        val packageManager = context.applicationContext.packageManager
        val packageInfo = packageManager.getPackageArchiveInfo(file.absolutePath, flags)
        val applicationInfo = packageInfo!!.applicationInfo
        applicationInfo.publicSourceDir = file.absolutePath
        applicationInfo.sourceDir = applicationInfo.publicSourceDir
        MMKVHelp.saveWebResPath(file.absolutePath)
        return packageManager.getResourcesForApplication(applicationInfo)
    }


    private fun getResourcesForApplication(file: File): Resources {
        val flags = (PackageManager.GET_META_DATA or PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES
                or PackageManager.GET_PROVIDERS or PackageManager.GET_RECEIVERS)
        val packageManager = context.applicationContext.packageManager
        val packageInfo = packageManager.getPackageArchiveInfo(file.absolutePath, flags)
        val applicationInfo = packageInfo!!.applicationInfo
        applicationInfo.publicSourceDir = file.absolutePath
        applicationInfo.sourceDir = applicationInfo.publicSourceDir
        return packageManager.getResourcesForApplication(applicationInfo)
    }

    fun startStandardActivity(context: Context, contentKey: String, activityName: String, packageName: String, intentOption: Intent? = null) {
        startActivity(context, contentKey, PluginStandardActivity, activityName, packageName, intentOption)
    }

    fun startPluginSingleInstanceActivity(context: Context, contentKey: String, activityName: String, packageName: String, intentOption: Intent? = null) {
        startActivity(context, contentKey, PluginSingleInstanceActivity, activityName, packageName, intentOption)
    }

    fun startPluginSingleTaskActivity(context: Context, contentKey: String, activityName: String, packageName: String, intentOption: Intent? = null) {
        startActivity(context, contentKey, PluginSingleTaskActivity, activityName, packageName, intentOption)
    }

    fun startPluginSingleTopActivity(context: Context, contentKey: String, activityName: String, packageName: String, intentOption: Intent? = null) {
        startActivity(context, contentKey, PluginSingleTopActivity, activityName, packageName, intentOption)
    }

    private fun startActivity(context: Context, contentKey: String, lunchName: String, activityName: String, packageName: String, intentOption: Intent? = null) {
        try {
            if (!cotd.containsKey(contentKey)) return
            val clazz = Class.forName(lunchName)
            val intent = intentOption ?: Intent(context, clazz)
            if (intentOption != null) {
                intent.setClass(context, clazz)
            }
            intent.apply {
                putExtra(activityNameKey, activityName)
                putExtra(privatePackageKey, packageName)
                val file = DynamicManageUtils.getDxFile(context, dldir, getDlfn(contentKey, cotd[contentKey]!!))
                if (!file.exists()) {
                    return
                }
                putExtra(pluginApkPathKey, file.absolutePath)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun startPluginStartStickyService(context: Context, contentKey: String, pluginServiceName: String, packageName: String, intentOption: Intent? = null) {
        startPluginService(context, contentKey, PluginStartStickyService, pluginServiceName, packageName, intentOption)
    }

    fun startPluginStartNotStickyService(context: Context, contentKey: String, pluginServiceName: String, packageName: String, intentOption: Intent? = null) {
        startPluginService(context, contentKey, PluginStartNotStickyService, pluginServiceName, packageName, intentOption)
    }

    fun startPluginStartRedeliverIntentService(context: Context, contentKey: String, pluginServiceName: String, packageName: String, intentOption: Intent? = null) {
        startPluginService(context, contentKey, PluginStartRedeliverIntentService, pluginServiceName, packageName, intentOption)
    }

    fun startPluginStickyCompatibilityService(context: Context, contentKey: String, pluginServiceName: String, packageName: String, intentOption: Intent? = null) {
        startPluginService(context, contentKey, PluginStartStickyCompatibilityService, pluginServiceName, packageName, intentOption)
    }

    fun startPluginProcessStartStickyService(context: Context, contentKey: String, pluginServiceName: String, packageName: String, intentOption: Intent? = null) {
        startPluginService(context, contentKey, PluginProcessStartStickyService, pluginServiceName, packageName, intentOption)
    }

    fun startPluginProcessStartNotStickyService(context: Context, contentKey: String, pluginServiceName: String, packageName: String, intentOption: Intent? = null) {
        startPluginService(context, contentKey, PluginProcessStartNotStickyService, pluginServiceName, packageName, intentOption)
    }

    fun startPluginProcessStartRedeliverIntentService(context: Context, contentKey: String, pluginServiceName: String, packageName: String, intentOption: Intent? = null) {
        startPluginService(context, contentKey, PluginProcessStartRedeliverIntentService, pluginServiceName, packageName, intentOption)
    }

    fun startPluginProcessStickyCompatibilityService(context: Context, contentKey: String, pluginServiceName: String, packageName: String, intentOption: Intent? = null) {
        startPluginService(context, contentKey, PluginProcessStartStickyCompatibilityService, pluginServiceName, packageName, intentOption)
    }

    private fun getServiceIntent(context: Context, contentKey: String, lunchName: String, pluginServiceName: String, packageName: String, intentOption: Intent? = null): Intent? {
        if (!cotd.containsKey(contentKey)) return null
        val clazz = Class.forName(lunchName)
        val intent = intentOption ?: Intent(context, clazz)
        if (intentOption != null) {
            intent.setClass(context, clazz)
        }
        intent.apply {
            putExtra(serviceNameKey, pluginServiceName)
            putExtra(privatePackageKey, packageName)
            val file = DynamicManageUtils.getDxFile(context, dldir, getDlfn(contentKey, cotd[contentKey]!!))
            if (!file.exists()) {
                return null
            }
            putExtra(pluginApkPathKey, file.absolutePath)
        }
        return intent
    }

    private fun startPluginService(context: Context, contentKey: String, lunchName: String, pluginServiceName: String, packageName: String, intentOption: Intent? = null) {
        try {
            getServiceIntent(context, contentKey, lunchName, pluginServiceName, packageName, intentOption)?.run {
                context.startService(this)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun bindStickyService(context: Context, pluginServiceName: String) {
        bindService(context, PluginStartStickyService, pluginServiceName)
    }

    fun bindNotStickyService(context: Context, pluginServiceName: String) {
        bindService(context, PluginStartNotStickyService, pluginServiceName)
    }

    fun bindRedeliverService(context: Context, pluginServiceName: String) {
        bindService(context, PluginStartRedeliverIntentService, pluginServiceName)
    }

    fun bindCompatibilityService(context: Context, pluginServiceName: String) {
        bindService(context, PluginStartStickyCompatibilityService, pluginServiceName)
    }

    fun bindProcessStickyService(context: Context, pluginServiceName: String) {
        bindService(context, PluginProcessStartStickyService, pluginServiceName)
    }

    fun bindProcessNotStickyService(context: Context, pluginServiceName: String) {
        bindService(context, PluginProcessStartNotStickyService, pluginServiceName)
    }

    fun bindProcessRedeliverService(context: Context, pluginServiceName: String) {
        bindService(context, PluginProcessStartRedeliverIntentService, pluginServiceName)
    }

    fun bindProcessCompatibilityService(context: Context, pluginServiceName: String) {
        bindService(context, PluginProcessStartStickyCompatibilityService, pluginServiceName)
    }

    private fun bindService(context: Context, hostServiceName: String, serviceName: String) {
        if (!mapAidl.containsKey(hostServiceName)) {
            val intent = Intent(context, Class.forName(hostServiceName)).putExtra(PluginKey.serviceNameKey, serviceName)
            context.bindService(intent, object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    mapAidl[hostServiceName] = WXDynamicAidlInterface.Stub.asInterface(service)
                }

                override fun onServiceDisconnected(name: ComponentName?) {

                }
            }, Context.BIND_AUTO_CREATE)
        } else {
            mapAidl[hostServiceName]?.onBind(serviceName)
        }
    }

    fun onAidlStickyServiceCallBack(PluginSerViceName: String, methodID: Int) = onAidlCallBack(PluginStartStickyService, PluginSerViceName, methodID)
    fun onAidlNotStickyServiceCallBack(PluginSerViceName: String, methodID: Int) = onAidlCallBack(PluginStartNotStickyService, PluginSerViceName, methodID)
    fun onAidlRedeliverServiceCallBack(PluginSerViceName: String, methodID: Int) = onAidlCallBack(PluginStartRedeliverIntentService, PluginSerViceName, methodID)
    fun onAidlCompatibilityServiceCallBack(PluginSerViceName: String, methodID: Int) = onAidlCallBack(PluginStartStickyCompatibilityService, PluginSerViceName, methodID)

    fun onProcessAidlStickyServiceCallBack(PluginSerViceName: String, methodID: Int) = onAidlCallBack(PluginProcessStartStickyService, PluginSerViceName, methodID)
    fun onProcessAidlNotStickyServiceCallBack(PluginSerViceName: String, methodID: Int) = onAidlCallBack(PluginProcessStartNotStickyService, PluginSerViceName, methodID)
    fun onProcessAidlRedeliverServiceCallBack(PluginSerViceName: String, methodID: Int) = onAidlCallBack(PluginProcessStartRedeliverIntentService, PluginSerViceName, methodID)
    fun onProcessAidlCompatibilityServiceCallBack(PluginSerViceName: String, methodID: Int) = onAidlCallBack(PluginProcessStartStickyCompatibilityService, PluginSerViceName, methodID)

    private fun onAidlCallBack(serviceName: String, PluginSerViceName: String, methodID: Int): String {
        if (mapAidl.containsKey(serviceName)) {
            mapAidl[serviceName]?.let {
                return it.onAidlCallBack(PluginSerViceName, methodID)
            }
        }
        return ""
    }

    fun deleteOldFile() {
        WXDynamicLoader.instance.loader.run {
            if (hasOldFileNeedDelete()) {
                val sbdex = StringBuilder(context.filesDir.absolutePath).apply {
                    append(File.separator)
                    append(dldir)
                    append(File.separator)
                }
                val fileDexDir = File(sbdex.toString())
                if (!fileDexDir.exists()) return@run
                val oatDir = "oat"
                sbdex.append(oatDir)
                val fileDexOatDir = File(sbdex.toString())
                val list = mutableListOf<String>()
                getMapDluImpl().forEach {
                    list.add(it.value.run { getDlfn(first, second) })
                }
                cotd.forEach {
                    list.add(it.run { getDlfn(key, value) })
                }
                list.add(clmd.run { getDlfn(second, third) })
                list.add(cdlfd.run { getDlfn(second, third) })
                fileDexDir.listFiles()?.forEach {
                    it?.name.let { fn ->
                        if (versionFile != fn && oatDir != fn && !list.contains(fn)) {
                            WLog.e(this@PluginManager, "文件:${fn}删除 ")
                            it.delete()
                        }
                    }
                }
                if (!fileDexOatDir.exists()) return@run
                fileDexOatDir.listFiles()?.forEach {
                    it?.name.let { fn ->
                        val oat = fn?.replace(".cur.prof", "")
                        if (versionFile != oat && !list.contains(oat)) {
                            WLog.e(this@PluginManager, "文件:${fn}删除 ")
                            it.delete()
                        }
                    }
                }
            }
        }
    }
}