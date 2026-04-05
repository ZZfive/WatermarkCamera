package com.watermarkcamera.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.watermarkcamera.ui.components.FontSizeSlider
import com.watermarkcamera.ui.components.GridPositionSelector
import com.watermarkcamera.ui.components.LargeButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "水印设置",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tab 选择器
            TabRow(
                selectedTabIndex = uiState.selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                TabItem(
                    selected = uiState.selectedTab == WatermarkBlockType.TIMESTAMP,
                    onClick = { viewModel.selectTab(WatermarkBlockType.TIMESTAMP) },
                    icon = Icons.Default.AccessTime,
                    text = "时间"
                )
                TabItem(
                    selected = uiState.selectedTab == WatermarkBlockType.ADDRESS,
                    onClick = { viewModel.selectTab(WatermarkBlockType.ADDRESS) },
                    icon = Icons.Default.LocationOn,
                    text = "地址"
                )
                TabItem(
                    selected = uiState.selectedTab == WatermarkBlockType.COORDS,
                    onClick = { viewModel.selectTab(WatermarkBlockType.COORDS) },
                    icon = Icons.Default.GpsFixed,
                    text = "经纬度"
                )
                TabItem(
                    selected = uiState.selectedTab == WatermarkBlockType.CUSTOM,
                    onClick = { viewModel.selectTab(WatermarkBlockType.CUSTOM) },
                    icon = Icons.Default.TextFields,
                    text = "文本"
                )
            }

            // Tab 内容
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                when (uiState.selectedTab) {
                    WatermarkBlockType.TIMESTAMP -> TimestampSettings(viewModel, uiState)
                    WatermarkBlockType.ADDRESS -> AddressSettings(viewModel, uiState)
                    WatermarkBlockType.COORDS -> CoordsSettings(viewModel, uiState)
                    WatermarkBlockType.CUSTOM -> CustomSettings(viewModel, uiState)
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 保存原图开关（全局设置，放在底部）
                Text(
                    text = "其他设置",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                SettingSwitchCard(
                    icon = Icons.Default.PhotoLibrary,
                    title = "保存原图",
                    subtitle = "同时保存一张不带水印的原图",
                    checked = uiState.saveOriginal,
                    onCheckedChange = { viewModel.updateSaveOriginal(it) }
                )

                Spacer(modifier = Modifier.height(32.dp))

                // 完成按钮
                LargeButton(
                    text = "完成",
                    onClick = onNavigateBack
                )
            }
        }
    }
}

@Composable
private fun TabItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    text: String
) {
    Tab(
        selected = selected,
        onClick = onClick,
        text = {
            Text(
                text = text,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        },
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = text,
                modifier = Modifier.size(20.dp)
            )
        }
    )
}

@Composable
private fun TimestampSettings(viewModel: SettingsViewModel, uiState: SettingsUiState) {
    val block = uiState.layoutConfig.timestamp

    BlockSettingsContent(
        title = "时间水印",
        subtitle = "拍照时显示当前时间",
        enabled = block.enabled,
        onEnabledChange = { viewModel.updateTimestampEnabled(it) },
        alignment = block.alignment,
        onAlignmentChange = { viewModel.updateTimestampAlignment(it) },
        fontSize = block.fontSizeSp,
        onFontSizeChange = { viewModel.updateTimestampFontSize(it) }
    )
}

@Composable
private fun AddressSettings(viewModel: SettingsViewModel, uiState: SettingsUiState) {
    val block = uiState.layoutConfig.address

    BlockSettingsContent(
        title = "地址水印",
        subtitle = "显示地理位置名称",
        enabled = block.enabled,
        onEnabledChange = { viewModel.updateAddressEnabled(it) },
        alignment = block.alignment,
        onAlignmentChange = { viewModel.updateAddressAlignment(it) },
        fontSize = block.fontSizeSp,
        onFontSizeChange = { viewModel.updateAddressFontSize(it) }
    )
}

@Composable
private fun CoordsSettings(viewModel: SettingsViewModel, uiState: SettingsUiState) {
    val block = uiState.layoutConfig.coords

    BlockSettingsContent(
        title = "经纬度水印",
        subtitle = "显示GPS坐标信息",
        enabled = block.enabled,
        onEnabledChange = { viewModel.updateCoordsEnabled(it) },
        alignment = block.alignment,
        onAlignmentChange = { viewModel.updateCoordsAlignment(it) },
        fontSize = block.fontSizeSp,
        onFontSizeChange = { viewModel.updateCoordsFontSize(it) }
    )
}

@Composable
private fun CustomSettings(viewModel: SettingsViewModel, uiState: SettingsUiState) {
    val block = uiState.layoutConfig.custom

    Column {
        BlockSettingsContent(
            title = "自定义文本",
            subtitle = "添加自定义文字到照片",
            enabled = block.enabled,
            onEnabledChange = { viewModel.updateCustomEnabled(it) },
            alignment = block.alignment,
            onAlignmentChange = { viewModel.updateCustomAlignment(it) },
            fontSize = block.fontSizeSp,
            onFontSizeChange = { viewModel.updateCustomFontSize(it) }
        )

        if (block.enabled) {
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = uiState.customText,
                onValueChange = { viewModel.updateCustomText(it) },
                label = { Text("自定义文本内容", fontSize = 16.sp) },
                placeholder = { Text("例如：项目验收", fontSize = 16.sp) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
        }
    }
}

@Composable
private fun BlockSettingsContent(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    alignment: com.watermarkcamera.watermark.WatermarkAlignment,
    onAlignmentChange: (com.watermarkcamera.watermark.WatermarkAlignment) -> Unit,
    fontSize: Int,
    onFontSizeChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Text(
                text = subtitle,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // 启用开关
            SettingSwitchCard(
                icon = Icons.Default.AccessTime,
                title = "启用",
                subtitle = "在照片上显示此水印",
                checked = enabled,
                onCheckedChange = onEnabledChange
            )

            if (enabled) {
                Spacer(modifier = Modifier.height(16.dp))

                // 位置选择器
                Text(
                    text = "位置",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                GridPositionSelector(
                    selected = alignment,
                    onSelected = onAlignmentChange
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 字号滑块
                Text(
                    text = "字体大小",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                FontSizeSlider(
                    value = fontSize,
                    onValueChange = onFontSizeChange
                )
            }
        }
    }
}

@Composable
private fun SettingSwitchCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}
