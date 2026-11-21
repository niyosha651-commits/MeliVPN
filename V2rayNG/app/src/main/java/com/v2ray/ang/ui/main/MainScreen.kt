package com.v2ray.ang.ui.main

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.ui.compose.QRCodeDialog
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private val MeliBackground = Color(0xFF050908)
private val MeliSurface = Color(0xFF0D1713)
private val MeliSurface2 = Color(0xFF13231C)
private val MeliGreen = Color(0xFF00F59B)
private val MeliCyan = Color(0xFF00D9D9)
private val MeliText = Color(0xFFE8FFF4)
private val MeliMuted = Color(0xFF789187)
private val MeliRed = Color(0xFFFF526B)

private enum class MeliTab {
    HOME, SERVERS, STATS, SUBSCRIPTION, SETTINGS
}

@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onNavigate: (MainDestination) -> Unit,
) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val groups = uiState.groups
    val isLoading by mainViewModel.isLoading.collectAsStateWithLifecycle()

    val isRunning = uiState.isRunning
    val displayText = mainViewModel.formatStatus(uiState.status)
    val selectedGuid = uiState.selectedGuid
    val doubleColumnDisplay = uiState.doubleColumnDisplay
    val confirmRemove = uiState.confirmRemove
    val shareQRCodeBitmap = uiState.shareQRCodeBitmap

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var currentTab by rememberSaveable { mutableStateOf(MeliTab.HOME) }

    var showSearch by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    var showDelAllConfirm by remember { mutableStateOf(false) }
    var showDelDuplicateConfirm by remember { mutableStateOf(false) }
    var showDelInvalidConfirm by remember { mutableStateOf(false) }

    var showRemoveConfirm by rememberSaveable(
        stateSaver = ServerDeleteTarget.Saver
    ) {
        mutableStateOf<ServerDeleteTarget?>(null)
    }

    var shareTarget by remember {
        mutableStateOf<Triple<String, ProfileItem, Boolean>?>(null)
    }

    val removeServer: (String, String) -> Unit = { guid, profileName ->
        if (confirmRemove) {
            showRemoveConfirm = ServerDeleteTarget(guid, profileName)
        } else {
            onAction(MainAction.RemoveServer(guid))
        }
    }

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { groups.size.coerceAtLeast(1) }
    )

    val lazyListStates = remember {
        mutableStateMapOf<String, LazyListState>()
    }

    val lazyGridStates = remember {
        mutableStateMapOf<String, LazyGridState>()
    }

    LaunchedEffect(groups) {
        val validIds = groups.map { it.id }.toSet()
        lazyListStates.keys.retainAll(validIds)
        lazyGridStates.keys.retainAll(validIds)
    }

    LaunchedEffect(groups, uiState.selectedGroupId) {
        if (groups.isEmpty()) return@LaunchedEffect

        val index = groups.indexOfFirst {
            it.id == uiState.selectedGroupId
        }.takeIf { it >= 0 } ?: 0

        if (!pagerState.isScrollInProgress &&
            pagerState.settledPage != index
        ) {
            pagerState.scrollToPage(index)
        }
    }

    val latestGroups by rememberUpdatedState(groups)

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val currentGroups = latestGroups

                if (page in currentGroups.indices) {
                    onAction(
                        MainAction.SelectGroup(currentGroups[page].id)
                    )
                }
            }
    }

    MainDialogs(
        showDelAllConfirm = showDelAllConfirm,
        onDismissDelAll = {
            showDelAllConfirm = false
        },
        onConfirmDelAll = {
            showDelAllConfirm = false
            onAction(MainAction.RemoveAllServers)
        },

        showDelDuplicateConfirm = showDelDuplicateConfirm,
        onDismissDelDuplicate = {
            showDelDuplicateConfirm = false
        },
        onConfirmDelDuplicate = {
            showDelDuplicateConfirm = false
            onAction(MainAction.RemoveDuplicateServers)
        },

        showDelInvalidConfirm = showDelInvalidConfirm,
        onDismissDelInvalid = {
            showDelInvalidConfirm = false
        },
        onConfirmDelInvalid = {
            showDelInvalidConfirm = false
            onAction(MainAction.RemoveInvalidServers)
        },

        showRemoveConfirm = showRemoveConfirm,
        onDismissRemove = {
            showRemoveConfirm = null
        },
        onConfirmRemove = { guid ->
            showRemoveConfirm = null
            onAction(MainAction.RemoveServer(guid))
        }
    )

    shareTarget?.let { target ->
        ShareMethodDialog(
            guid = target.first,
            profile = target.second,
            more = target.third,
            onDismiss = {
                shareTarget = null
            },
            onAction = onAction,
            onRemove = removeServer
        )
    }

    if (shareQRCodeBitmap != null) {
        QRCodeDialog(
            bitmap = shareQRCodeBitmap,
            onDismiss = {
                onAction(MainAction.DismissQRCodeDialog)
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            MainDrawerContent(
                drawerState = drawerState,
                onNavigate = { route ->
                    scope.launch {
                        drawerState.close()
                    }
                    onNavigate(route)
                }
            )
        }
    ) {
        Scaffold(
            containerColor = MeliBackground,

            topBar = {
                MainTopBar(
                    isLoading = isLoading,
                    showSearch = showSearch && currentTab == MeliTab.SERVERS,
                    searchQuery = searchQuery,

                    onSearchQueryChange = { query ->
                        searchQuery = query
                        onAction(MainAction.Search(query))
                    },

                    onSearchClose = {
                        searchQuery = ""
                        onAction(MainAction.Search(""))
                        showSearch = false
                    },

                    onSearchToggle = { show ->
                        showSearch = show
                    },

                    onMenuClick = {
                        scope.launch {
                            drawerState.open()
                        }
                    },

                    onAction = onAction,

                    onMoreMenuAction = { action ->
                        when (action) {
                            MainMoreMenuAction.RestartService ->
                                onAction(MainAction.RestartService)

                            MainMoreMenuAction.DeleteAll ->
                                showDelAllConfirm = true

                            MainMoreMenuAction.DeleteDuplicate ->
                                showDelDuplicateConfirm = true

                            MainMoreMenuAction.DeleteInvalid ->
                                showDelInvalidConfirm = true

                            MainMoreMenuAction.ExportAll ->
                                onAction(MainAction.ExportAll)

                            MainMoreMenuAction.LocateSelected ->
                                onAction(MainAction.LocateSelectedServer)

                            MainMoreMenuAction.SortByTestResults ->
                                onAction(MainAction.SortByTestResults)

                            MainMoreMenuAction.TestAll ->
                                onAction(MainAction.TestAllServers)

                            MainMoreMenuAction.TestAllRealPing ->
                                onAction(MainAction.TestRealAllServers)

                            MainMoreMenuAction.UpdateSubscriptions ->
                                onAction(MainAction.UpdateSubscriptions)
                        }
                    }
                )
            },

            bottomBar = {
                MeliBottomNavigation(
                    selected = currentTab,
                    onSelect = { tab ->
                        currentTab = tab

                        if (tab == MeliTab.SUBSCRIPTION) {
                            onNavigate(MainDestination.Subscriptions)
                        }

                        if (tab == MeliTab.SETTINGS) {
                            onNavigate(MainDestination.Settings)
                        }
                    }
                )
            }

        ) { innerPadding ->

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MeliBackground)
            ) {

                when (currentTab) {

                    MeliTab.HOME -> {
                        HomeScreen(
                            isRunning = isRunning,
                            statusText = displayText,
                            onToggle = {
                                onAction(MainAction.ToggleService)
                            },
                            onOpenServers = {
                                currentTab = MeliTab.SERVERS
                            }
                        )
                    }

                    MeliTab.SERVERS -> {
                        ServersScreen(
                            groups = groups,
                            pagerState = pagerState,
                            mainViewModel = mainViewModel,
                            selectedGuid = selectedGuid,
                            locateTarget = uiState.locateTarget,
                            doubleColumnDisplay = doubleColumnDisplay,
                            searchQuery = searchQuery,
                            lazyListStates = lazyListStates,
                            lazyGridStates = lazyGridStates,
                            onSelectServer = { guid ->
                                onAction(MainAction.SelectServer(guid))
                            },
                            onEditServer = { guid, profile ->
                                onAction(MainAction.EditServer(guid, profile))
                            },
                            onShareServer = { guid, profile ->
                                shareTarget = Triple(guid, profile, false)
                            },
                            onMoreServer = { guid, profile ->
                                shareTarget = Triple(guid, profile, true)
                            },
                            onRemoveServer = removeServer
                        )
                    }

                    MeliTab.STATS -> {
                        StatsScreen(
                            isRunning = isRunning,
                            statusText = displayText
                        )
                    }

                    MeliTab.SUBSCRIPTION -> {
                        SubscriptionScreen(
                            onOpen = {
                                onNavigate(MainDestination.Subscriptions)
                            }
                        )
                    }

                    MeliTab.SETTINGS -> {
                        SettingsScreen(
                            onOpen = {
                                onNavigate(MainDestination.Settings)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MeliBottomNavigation(
    selected: MeliTab,
    onSelect: (MeliTab) -> Unit
) {
    NavigationBar(
        containerColor = Color(0xFF080F0C),
        contentColor = MeliGreen,
        tonalElevation = 0.dp
    ) {
        val items = listOf(
            Triple(MeliTab.HOME, "⌂", "HOME"),
            Triple(MeliTab.SERVERS, "◎", "SERVERS"),
            Triple(MeliTab.STATS, "▥", "STATS"),
            Triple(MeliTab.SUBSCRIPTION, "◇", "PRO"),
            Triple(MeliTab.SETTINGS, "⚙", "SETTINGS")
        )

        items.forEach { item ->
            NavigationBarItem(
                selected = selected == item.first,
                onClick = {
                    onSelect(item.first)
                },
                icon = {
                    Text(
                        text = item.second,
                        fontSize = 21.sp,
                        color = if (selected == item.first) {
                            MeliGreen
                        } else {
                            MeliMuted
                        }
                    )
                },
                label = {
                    Text(
                        item.third,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MeliGreen,
                    selectedTextColor = MeliGreen,
                    indicatorColor = MeliSurface2,
                    unselectedIconColor = MeliMuted,
                    unselectedTextColor = MeliMuted
                )
            )
        }
    }
}

@Composable
private fun HomeScreen(
    isRunning: Boolean,
    statusText: String,
    onToggle: () -> Unit,
    onOpenServers: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "meli_connection")

    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1700,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "MELI",
                    color = MeliGreen,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "SECURE CONNECTION",
                    color = MeliMuted,
                    fontSize = 10.sp,
                    letterSpacing = 2.sp
                )
            }

            Spacer(Modifier.weight(1f))

            Surface(
                shape = RoundedCornerShape(30.dp),
                color = if (isRunning) {
                    MeliGreen.copy(alpha = 0.12f)
                } else {
                    MeliSurface2
                },
                border = BorderStroke(
                    1.dp,
                    if (isRunning) MeliGreen else MeliMuted
                )
            ) {
                Text(
                    if (isRunning) "● ONLINE" else "● OFFLINE",
                    modifier = Modifier.padding(
                        horizontal = 13.dp,
                        vertical = 8.dp
                    ),
                    color = if (isRunning) MeliGreen else MeliMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        WorldMapDecoration(
            modifier = Modifier
                .fillMaxWidth()
                .height(155.dp)
        )

        Spacer(Modifier.height(20.dp))

        Text(
            if (isRunning) "YOU ARE PROTECTED" else "YOUR CONNECTION",
            color = MeliText,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(Modifier.height(8.dp))

        Text(
            if (isRunning) "Your VPN connection is active"
            else "Connect to start your secure session",
            color = MeliMuted,
            fontSize = 12.sp
        )

        Spacer(Modifier.height(30.dp))

        Box(
            modifier = Modifier
                .size(220.dp)
                .clip(CircleShape)
                .background(
                    MeliGreen.copy(alpha = 0.035f)
                )
                .border(
                    1.dp,
                    MeliGreen.copy(alpha = 0.25f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(185.dp)
                    .clip(CircleShape)
                    .border(
                        1.dp,
                        MeliCyan.copy(alpha = 0.3f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.size(
                        (145 * pulse).dp
                    ),
                    shape = CircleShape,
                    color = if (isRunning) {
                        MeliGreen
                    } else {
                        MeliSurface2
                    },
                    border = BorderStroke(
                        2.dp,
                        if (isRunning) MeliGreen else MeliMuted
                    ),
                    shadowElevation = 18.dp,
                    onClick = onToggle
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            if (isRunning) "Ⅱ" else "⏻",
                            fontSize = 39.sp,
                            color = if (isRunning) {
                                Color.Black
                            } else {
                                MeliGreen
                            }
                        )

                        Text(
                            if (isRunning) "DISCONNECT" else "CONNECT",
                            color = if (isRunning) {
                                Color.Black
                            } else {
                                MeliText
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        Text(
            statusText,
            color = if (isRunning) MeliGreen else MeliMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(28.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenServers),
            shape = RoundedCornerShape(18.dp),
            color = MeliSurface,
            border = BorderStroke(
                1.dp,
                MeliGreen.copy(alpha = 0.22f)
            )
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(43.dp),
                    shape = CircleShape,
                    color = MeliSurface2
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("◎", color = MeliGreen, fontSize = 25.sp)
                    }
                }

                Spacer(Modifier.width(14.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        "SELECT SERVER",
                        color = MeliMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        "Choose your server",
                        color = MeliText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    "›",
                    color = MeliGreen,
                    fontSize = 27.sp
                )
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun WorldMapDecoration(
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val spacing = 13.dp.toPx()

        for (x in 0..35) {
            for (y in 0..11) {
                val nx = x / 35f
                val ny = y / 11f

                val shape = (
                    kotlin.math.sin(nx * 18f) *
                    kotlin.math.cos(ny * 9f)
                )

                if (shape > -0.3f) {
                    drawCircle(
                        color = MeliGreen.copy(alpha = 0.18f),
                        radius = 1.7.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(
                            x * spacing + 5.dp.toPx(),
                            y * spacing + 5.dp.toPx()
                        )
                    )
                }
            }
        }

        drawCircle(
            color = MeliCyan.copy(alpha = 0.7f),
            radius = 4.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(
                size.width * 0.67f,
                size.height * 0.42f
            )
        )
    }
}

@Composable
private fun ServersScreen(
    groups: List<com.v2ray.ang.dto.GroupMapItem>,
    pagerState: androidx.compose.foundation.pager.PagerState,
    mainViewModel: MainViewModel,
    selectedGuid: String?,
    locateTarget: com.v2ray.ang.dto.LocateTarget?,
    doubleColumnDisplay: Boolean,
    searchQuery: String,
    lazyListStates: MutableMap<String, LazyListState>,
    lazyGridStates: MutableMap<String, LazyGridState>,
    onSelectServer: (String) -> Unit,
    onEditServer: (String, ProfileItem) -> Unit,
    onShareServer: (String, ProfileItem) -> Unit,
    onMoreServer: (String, ProfileItem) -> Unit,
    onRemoveServer: (String, String) -> Unit
) {
    if (groups.isEmpty()) {
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "NO SERVERS",
                    color = MeliGreen,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Import a configuration to get started.",
                    color = MeliMuted,
                    fontSize = 12.sp
                )
            }
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        if (groups.size > 1) {
            GroupTabBar(
                groups = groups,
                selectedTabIndex = pagerState.currentPage.coerceIn(
                    0,
                    groups.lastIndex
                ),
                mainViewModel = mainViewModel,
                onTabClick = { targetIndex ->
                    kotlinx.coroutines.MainScope().launch {
                        pagerState.navigateToPageOptimized(
                            targetPage = targetIndex,
                            animateAdjacentPage = true
                        )
                    }
                }
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = true,
            beyondViewportPageCount = 1,
            key = { page ->
                groups.getOrNull(page)?.id ?: "group-page-$page"
            }
        ) { page ->
            val group = groups.getOrNull(page)
                ?: return@HorizontalPager

            GroupPagerPage(
                groupId = group.id,
                mainViewModel = mainViewModel,
                selectedGuid = selectedGuid,
                locateTarget = locateTarget,
                doubleColumnDisplay = doubleColumnDisplay,
                searchQuery = searchQuery,
                lazyListStates = lazyListStates,
                lazyGridStates = lazyGridStates,
                onSelectServer = onSelectServer,
                onEditServer = onEditServer,
                onShareServer = onShareServer,
                onMoreServer = onMoreServer,
                onRemoveServer = onRemoveServer,
                usageInfo = group.usageInfo,
                groupTitle = group.remarks,
                onOpenLink = { url ->
                    mainViewModel.onAction(
                        MainAction.OpenSubscriptionLink(url)
                    )
                },
                contentPadding = PaddingValues(
                    start = 0.dp,
                    top = 4.dp,
                    end = 0.dp,
                    bottom = 16.dp
                )
            )
        }
    }
}

@Composable
private fun StatsScreen(
    isRunning: Boolean,
    statusText: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            "CONNECTION STATS",
            color = MeliGreen,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Live connection status",
            color = MeliMuted,
            fontSize = 12.sp
        )

        Spacer(Modifier.height(24.dp))

        StatsCard(
            title = "VPN STATUS",
            value = if (isRunning) "CONNECTED" else "DISCONNECTED",
            accent = if (isRunning) MeliGreen else MeliRed
        )

        Spacer(Modifier.height(12.dp))

        StatsCard(
            title = "SERVICE MESSAGE",
            value = statusText,
            accent = MeliCyan
        )

        Spacer(Modifier.height(18.dp))

        Text(
            "Additional traffic statistics will appear here when the VPN service exposes real-time traffic data.",
            color = MeliMuted,
            fontSize = 12.sp,
            lineHeight = 19.sp
        )
    }
}

@Composable
private fun StatsCard(
    title: String,
    value: String,
    accent: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MeliSurface,
        border = BorderStroke(
            1.dp,
            accent.copy(alpha = 0.3f)
        )
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                title,
                color = MeliMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(Modifier.height(9.dp))

            Text(
                value,
                color = accent,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SubscriptionScreen(
    onOpen: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            "MEMBERSHIP",
            color = MeliGreen,
            fontSize = 23.sp,
            fontWeight = FontWeight.Black
        )

        Text(
            "Choose your MeliVPN experience",
            color = MeliMuted,
            fontSize = 12.sp
        )

        Spacer(Modifier.height(25.dp))

        MembershipCard(
            name = "PRO",
            description = "Premium server access",
            accent = MeliCyan
        )

        Spacer(Modifier.height(13.dp))

        MembershipCard(
            name = "PRO PREMIUM",
            description = "Enhanced premium experience",
            accent = MeliGreen
        )

        Spacer(Modifier.height(13.dp))

        MembershipCard(
            name = "INFINITY",
            description = "Exclusive membership experience",
            accent = Color(0xFFB58AFF)
        )

        Spacer(Modifier.height(25.dp))

        Button(
            onClick = onOpen,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MeliGreen,
                contentColor = Color.Black
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                "OPEN SUBSCRIPTION MANAGER",
                fontWeight = FontWeight.Black
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            "Membership activation and payments are managed by the existing subscription system.",
            color = MeliMuted,
            fontSize = 11.sp,
            lineHeight = 17.sp
        )
    }
}

@Composable
private fun MembershipCard(
    name: String,
    description: String,
    accent: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        color = MeliSurface,
        border = BorderStroke(
            1.dp,
            accent.copy(alpha = 0.55f)
        )
    ) {
        Row(
            modifier = Modifier.padding(19.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "◆",
                    color = accent,
                    fontSize = 20.sp
                )
            }

            Spacer(Modifier.width(14.dp))

            Column {
                Text(
                    name,
                    color = accent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    description,
                    color = MeliMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    onOpen: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text(
            "SETTINGS",
            color = MeliGreen,
            fontSize = 23.sp,
            fontWeight = FontWeight.Black
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Manage your application preferences",
            color = MeliMuted,
            fontSize = 12.sp
        )

        Spacer(Modifier.height(25.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen),
            shape = RoundedCornerShape(16.dp),
            color = MeliSurface,
            border = BorderStroke(
                1.dp,
                MeliGreen.copy(alpha = 0.3f)
            )
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "⚙",
                    color = MeliGreen,
                    fontSize = 26.sp
                )

                Spacer(Modifier.width(15.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        "APP SETTINGS",
                        color = MeliText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        "Open all existing VPN settings",
                        color = MeliMuted,
                        fontSize = 11.sp
                    )
                }

                Text(
                    "›",
                    color = MeliGreen,
                    fontSize = 27.sp
                )
            }
        }
    }
}
