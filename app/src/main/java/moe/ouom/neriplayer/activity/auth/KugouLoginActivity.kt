package moe.ouom.neriplayer.activity.auth

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import moe.ouom.neriplayer.core.di.AppContainer
import moe.ouom.neriplayer.core.logging.NPLogger
import androidx.lifecycle.lifecycleScope
import moe.ouom.neriplayer.ui.screen.tab.settings.miuix.MiuixSettingsSegmentedTabs

class KugouLoginActivity : ComponentActivity() {

    companion object {
        private const val TAG = "KugouLogin"
        private const val POLL_INTERVAL_MS = 2000L
        private const val QR_SIZE_PX = 600
        private const val COUNTDOWN_SECONDS = 60
    }

    private val kugouClient = AppContainer.kugouClient

    private var pollJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

        setContent {
            val useDarkTheme = isSystemInDarkTheme()
            MaterialTheme(
                colorScheme = (if (useDarkTheme) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                        dynamicDarkColorScheme(LocalContext.current)
                    else
                        darkColorScheme()
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                        dynamicLightColorScheme(LocalContext.current)
                    else
                        lightColorScheme()
                }))
            {
                CompositionLocalProvider(
                    LocalContentColor provides contentColorFor(MaterialTheme.colorScheme.surface),
                ) {
                    KugouLoginScreen()
                }

            }

        }
    }

    override fun onDestroy() {
        pollJob?.cancel()
        super.onDestroy()
    }

    private fun handleLoginSuccess(token: String, userid: String) {
        NPLogger.d(TAG, "Login success: token=$token, userid=$userid")
        val cookies = mapOf("token" to token, "userid" to userid)
        AppContainer.kugouCookieRepo.saveCookies(cookies)
        setResult(Activity.RESULT_OK)
        finish()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun KugouLoginScreen() {
        var selectedTab by rememberSaveable { mutableIntStateOf(0) }
        val tabLabels = listOf("扫码登录", "验证码登录", "密码登录", "刷新Token")

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("酷狗音乐登录") },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                MiuixSettingsSegmentedTabs(
                    labels = tabLabels,
                    selectedIndex = selectedTab,
                    onSelectedIndexChange = { selectedTab = it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally { it } + fadeIn()) togetherWith
                                (slideOutHorizontally { -it } + fadeOut())
                        } else {
                            (slideInHorizontally { -it } + fadeIn()) togetherWith
                                (slideOutHorizontally { it } + fadeOut())
                        }
                    },
                    label = "login_tab"
                ) { tab ->
                    when (tab) {
                        0 -> QrLoginContent()
                        1 -> PhoneCodeLoginContent()
                        2 -> PasswordLoginContent()
                        3 -> TokenRefreshContent()
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  QR Login (preserves original logic exactly)
    // ─────────────────────────────────────────────────────────────

    @Composable
    private fun QrLoginContent() {
        var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
        var statusText by remember { mutableStateOf("正在获取二维码...") }
        var isLoading by remember { mutableStateOf(true) }
        var showError by remember { mutableStateOf(false) }
        var showRetry by remember { mutableStateOf(false) }

        val scope = rememberCoroutineScope()

        fun startQrLogin() {
            pollJob?.cancel()
            isLoading = true
            showError = false
            showRetry = false
            qrBitmap = null
            statusText = "正在获取二维码..."

            scope.launch {
                try {
                    val response = withContext(Dispatchers.IO) {
                        kugouClient.createQrKey()
                    }
                    if (response.status != 200) throw Exception("HTTP ${response.status}")

                    val data = response.body["data"]?.jsonObject ?: throw Exception("Invalid data")
                    val qrcode = data["qrcode"]?.jsonPrimitive?.content ?: throw Exception("Missing key")
                    val url = kugouClient.createQrCodeUrl(qrcode)

                    val bitmap = withContext(Dispatchers.Default) { createQrBitmap(url) }
                    qrBitmap = bitmap
                    isLoading = false
                    statusText = "请使用酷狗音乐 App 扫码"

                    startPolling(qrcode) { newStatus, retry, done ->
                        statusText = newStatus
                        showRetry = retry
                        if (done) {
                            pollJob?.cancel()
                        }
                    }
                } catch (e: Exception) {
                    NPLogger.e(TAG, "Failed to start QR login", e)
                    statusText = "加载失败: ${e.message}"
                    isLoading = false
                    showError = true
                    showRetry = true
                }
            }
        }

        DisposableEffect(Unit) {
            startQrLogin()
            onDispose { pollJob?.cancel() }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            Icon(
                imageVector = Icons.Outlined.QrCode,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(16.dp))

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(16.dp))
            }

            AnimatedVisibility(visible = qrBitmap != null && !isLoading) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .background(Color.White, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        qrBitmap?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "QR Code",
                                modifier = Modifier.size(220.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyLarge,
                color = if (showError) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (showRetry) {
                Spacer(Modifier.height(16.dp))
                Button(onClick = { startQrLogin() }) {
                    Text("重试")
                }
            }

            Spacer(Modifier.height(32.dp))

            Text(
                text = "打开酷狗音乐 App → 我的 → 右上角扫一扫",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }

    private fun startPolling(
        key: String,
        onStatusChange: (status: String, showRetry: Boolean, done: Boolean) -> Unit
    ) {
        pollJob = lifecycleScope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                try {
                    val response = withContext(Dispatchers.IO) {
                        kugouClient.checkQrCode(key)
                    }
                    if (response.status != 200) continue

                    val data = response.body["data"]?.jsonObject ?: continue
                    val status = data["status"]?.jsonPrimitive?.intOrNull

                    when (status) {
                        1 -> onStatusChange("等待扫码", false, false)
                        2 -> onStatusChange("等待确认", false, false)
                        4 -> {
                            val token = data["token"]?.jsonPrimitive?.content ?: ""
                            val userid = data["userid"]?.jsonPrimitive?.content ?: ""
                            if (token.isNotEmpty() && userid.isNotEmpty()) {
                                onStatusChange("登录成功", false, true)
                                handleLoginSuccess(token, userid)
                            }
                        }
                        0 -> {
                            onStatusChange("二维码已过期", true, true)
                        }
                    }
                } catch (e: Exception) {
                    NPLogger.w(TAG, "Poll failed: ${e.message}")
                }
            }
        }
    }

    private fun createQrBitmap(content: String): Bitmap {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val bitMatrix = QRCodeWriter().encode(
            content, BarcodeFormat.QR_CODE, QR_SIZE_PX, QR_SIZE_PX, hints
        )
        val bitmap = Bitmap.createBitmap(QR_SIZE_PX, QR_SIZE_PX, Bitmap.Config.RGB_565)
        for (x in 0 until QR_SIZE_PX) {
            for (y in 0 until QR_SIZE_PX) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        return bitmap
    }

    // ─────────────────────────────────────────────────────────────
    //  Phone + SMS Code Login
    // ─────────────────────────────────────────────────────────────

    @Composable
    private fun PhoneCodeLoginContent() {
        var phone by rememberSaveable { mutableStateOf("") }
        var code by rememberSaveable { mutableStateOf("") }
        var isSendingCode by remember { mutableStateOf(false) }
        var isLoggingIn by remember { mutableStateOf(false) }
        var countdown by remember { mutableIntStateOf(0) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var successMessage by remember { mutableStateOf<String?>(null) }

        val scope = rememberCoroutineScope()
        val focusManager = LocalFocusManager.current

        // Countdown timer
        LaunchedEffect(countdown) {
            if (countdown > 0) {
                delay(1000)
                countdown--
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            Icon(
                imageVector = Icons.Outlined.Phone,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "使用手机号 + 验证码登录",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(Modifier.height(24.dp))

            // Phone number input
            OutlinedTextField(
                value = phone,
                onValueChange = {
                    phone = it.filter { c -> c.isDigit() }.take(11)
                    errorMessage = null
                },
                label = { Text("手机号") },
                placeholder = { Text("请输入手机号") },
                leadingIcon = { Icon(Icons.Outlined.Phone, contentDescription = null) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Next
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoggingIn
            )

            Spacer(Modifier.height(12.dp))

            // Code input + send button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = code,
                    onValueChange = {
                        code = it.filter { c -> c.isDigit() }.take(6)
                        errorMessage = null
                    },
                    label = { Text("验证码") },
                    placeholder = { Text("6位验证码") },
                    leadingIcon = { Icon(Icons.Outlined.Sms, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        focusManager.clearFocus()
                        if (phone.length >= 11 && code.length >= 4) {
                            isLoggingIn = true
                            errorMessage = null
                            scope.launch {
                                doPhoneCodeLogin(phone, code,
                                    onSuccess = { isLoggingIn = false },
                                    onError = { msg ->
                                        isLoggingIn = false
                                        errorMessage = msg
                                    }
                                )
                            }
                        }
                    }),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    enabled = !isLoggingIn
                )

                OutlinedButton(
                    onClick = {
                        focusManager.clearFocus()
                        isSendingCode = true
                        errorMessage = null
                        scope.launch {
                            try {
                                val response = withContext(Dispatchers.IO) {
                                    kugouClient.auth.sendCaptcha(phone)
                                }
                                if (response.status == 200) {
                                    val errCode = response.body["err_code"]?.jsonPrimitive?.intOrNull
                                        ?: response.body["status"]?.jsonPrimitive?.intOrNull
                                    if (errCode == 0 || errCode == 1) {
                                        countdown = COUNTDOWN_SECONDS
                                        successMessage = "验证码已发送"
                                    } else {
                                        val msg = response.body["error"]?.jsonPrimitive?.content
                                            ?: response.body["msg"]?.jsonPrimitive?.content
                                            ?: "发送失败 (code=$errCode)"
                                        errorMessage = msg
                                    }
                                } else {
                                    errorMessage = "请求失败: ${response.status}"
                                }
                            } catch (e: Exception) {
                                NPLogger.e(TAG, "sendCaptcha failed", e)
                                errorMessage = "发送失败: ${e.message}"
                            } finally {
                                isSendingCode = false
                            }
                        }
                    },
                    enabled = phone.length >= 11 && countdown == 0 && !isSendingCode && !isLoggingIn,
                    modifier = Modifier
                        .height(56.dp)
                        .padding(top = 15.dp)
                ) {
                    if (isSendingCode) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else if (countdown > 0) {
                        Text("${countdown}s")
                    } else {
                        Text("发送验证码")
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Login button
            Button(
                onClick = {
                    focusManager.clearFocus()
                    isLoggingIn = true
                    errorMessage = null
                    scope.launch {
                        doPhoneCodeLogin(phone, code,
                            onSuccess = { isLoggingIn = false },
                            onError = { msg ->
                                isLoggingIn = false
                                errorMessage = msg
                            }
                        )
                    }
                },
                enabled = phone.length >= 11 && code.length >= 4 && !isLoggingIn,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (isLoggingIn) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text("登录")
            }

            // Error message
            AnimatedVisibility(visible = errorMessage != null) {
                Text(
                    text = errorMessage ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            // Success message
            AnimatedVisibility(visible = successMessage != null) {
                Text(
                    text = successMessage ?: "",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }

    private suspend fun doPhoneCodeLogin(
        phone: String,
        code: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val response = withContext(Dispatchers.IO) {
                kugouClient.auth.loginByPhoneCode(phone, code)
            }
            if (response.status != 200) {
                onError("请求失败: HTTP ${response.status}")
                return
            }
            val errCode = response.body["err_code"]?.jsonPrimitive?.intOrNull
                ?: response.body["status"]?.jsonPrimitive?.intOrNull ?: -1
            if (errCode != 0 && errCode != 1) {
                val msg = response.body["error"]?.jsonPrimitive?.content
                    ?: response.body["msg"]?.jsonPrimitive?.content
                    ?: "登录失败 (code=$errCode)"
                onError(msg)
                return
            }
            val data = response.body["data"]?.jsonObject
            val token = data?.get("token")?.jsonPrimitive?.content ?: ""
            val userid = data?.get("userid")?.jsonPrimitive?.content ?: ""
            if (token.isBlank() || userid.isBlank() || userid == "0") {
                onError("登录响应缺少 token 或 userid")
                return
            }
            onSuccess()
            handleLoginSuccess(token, userid)
        } catch (e: Exception) {
            NPLogger.e(TAG, "loginByPhoneCode failed", e)
            onError("登录失败: ${e.message}")
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Password Login
    // ─────────────────────────────────────────────────────────────

    @Composable
    private fun PasswordLoginContent() {
        var username by rememberSaveable { mutableStateOf("") }
        var password by rememberSaveable { mutableStateOf("") }
        var passwordVisible by remember { mutableStateOf(false) }
        var isLoggingIn by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        val scope = rememberCoroutineScope()
        val focusManager = LocalFocusManager.current

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "使用酷狗账号密码登录",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(Modifier.height(24.dp))

            // Username input
            OutlinedTextField(
                value = username,
                onValueChange = {
                    username = it
                    errorMessage = null
                },
                label = { Text("账号") },
                placeholder = { Text("酷狗号 / 手机号 / 邮箱") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoggingIn
            )

            Spacer(Modifier.height(12.dp))

            // Password input
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    errorMessage = null
                },
                label = { Text("密码") },
                placeholder = { Text("请输入密码") },
                leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Outlined.Visibility
                            else Icons.Outlined.VisibilityOff,
                            contentDescription = if (passwordVisible) "隐藏密码" else "显示密码"
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = {
                    focusManager.clearFocus()
                    if (username.isNotBlank() && password.isNotBlank()) {
                        isLoggingIn = true
                        errorMessage = null
                        scope.launch {
                            doPasswordLogin(username, password,
                                onSuccess = { isLoggingIn = false },
                                onError = { msg ->
                                    isLoggingIn = false
                                    errorMessage = msg
                                }
                            )
                        }
                    }
                }),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoggingIn
            )

            Spacer(Modifier.height(24.dp))

            // Login button
            Button(
                onClick = {
                    focusManager.clearFocus()
                    isLoggingIn = true
                    errorMessage = null
                    scope.launch {
                        doPasswordLogin(username, password,
                            onSuccess = { isLoggingIn = false },
                            onError = { msg ->
                                isLoggingIn = false
                                errorMessage = msg
                            }
                        )
                    }
                },
                enabled = username.isNotBlank() && password.isNotBlank() && !isLoggingIn,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (isLoggingIn) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text("登录")
            }

            // Error message
            AnimatedVisibility(visible = errorMessage != null) {
                Text(
                    text = errorMessage ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }

    private suspend fun doPasswordLogin(
        username: String,
        password: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val response = withContext(Dispatchers.IO) {
                kugouClient.auth.loginByPassword(username, password)
            }
            if (response.status != 200) {
                onError("请求失败: HTTP ${response.status}")
                return
            }
            val errCode = response.body["err_code"]?.jsonPrimitive?.intOrNull
                ?: response.body["status"]?.jsonPrimitive?.intOrNull ?: -1
            if (errCode != 0 && errCode != 1) {
                val msg = response.body["error"]?.jsonPrimitive?.content
                    ?: response.body["msg"]?.jsonPrimitive?.content
                    ?: "登录失败 (code=$errCode)"
                onError(msg)
                return
            }
            val data = response.body["data"]?.jsonObject
            val token = data?.get("token")?.jsonPrimitive?.content ?: ""
            val userid = data?.get("userid")?.jsonPrimitive?.content ?: ""
            if (token.isBlank() || userid.isBlank() || userid == "0") {
                onError("登录响应缺少 token 或 userid")
                return
            }
            onSuccess()
            handleLoginSuccess(token, userid)
        } catch (e: Exception) {
            NPLogger.e(TAG, "loginByPassword failed", e)
            onError("登录失败: ${e.message}")
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Token Refresh
    // ─────────────────────────────────────────────────────────────

    @Composable
    private fun TokenRefreshContent() {
        var isRefreshing by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var successMessage by remember { mutableStateOf<String?>(null) }

        val scope = rememberCoroutineScope()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "使用已有 Token 刷新登录状态",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "将从本地存储读取已保存的 Token 和 UserId 进行刷新。\n适用于 Token 过期但 UserId 仍有效的情况。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    isRefreshing = true
                    errorMessage = null
                    successMessage = null
                    scope.launch {
                        doTokenRefresh(
                            onSuccess = {
                                isRefreshing = false
                                successMessage = "Token 刷新成功"
                            },
                            onError = { msg ->
                                isRefreshing = false
                                errorMessage = msg
                            }
                        )
                    }
                },
                enabled = !isRefreshing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text("刷新 Token")
            }

            // Error message
            AnimatedVisibility(visible = errorMessage != null) {
                Text(
                    text = errorMessage ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            // Success message
            AnimatedVisibility(visible = successMessage != null) {
                Text(
                    text = successMessage ?: "",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }

    private suspend fun doTokenRefresh(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            // 从已保存的 Cookie 中读取 token 和 userid
            kugouClient.seedFromRepository()
            kugouClient.dumpCookies()

            val response = withContext(Dispatchers.IO) {
                kugouClient.auth.loginByToken()
            }
            if (response.status != 200) {
                onError("请求失败: HTTP ${response.status},${response.body}")
                return
            }
            val errCode = response.body["err_code"]?.jsonPrimitive?.intOrNull
                ?: response.body["status"]?.jsonPrimitive?.intOrNull ?: -1
            if (errCode != 0 && errCode != 1) {
                val msg = response.body["error"]?.jsonPrimitive?.content
                    ?: response.body["msg"]?.jsonPrimitive?.content
                    ?: "刷新失败 (code=$errCode)"
                onError(msg)
                return
            }
            val data = response.body["data"]?.jsonObject
            val newToken = data?.get("token")?.jsonPrimitive?.content ?: ""
            val newUserid = data?.get("userid")?.jsonPrimitive?.content ?: "0"
            NPLogger.d(TAG, "Data=$data Token=$newToken, newUserid=$newUserid")
            if (newToken.isBlank() || newUserid.isBlank() || newUserid == "0") {
                onError("刷新响应缺少 token 或 userid")
                return
            }
            NPLogger.d("${ kugouClient.youth.getUnionVip() }")
            onSuccess()
            handleLoginSuccess(newToken, newUserid)
        } catch (e: Exception) {
            NPLogger.e(TAG, "loginByToken failed", e)
            onError("刷新失败: ${e.message}")
        }
    }
}
