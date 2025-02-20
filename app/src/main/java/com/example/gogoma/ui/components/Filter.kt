package com.example.gogoma.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.gogoma.R
import com.example.gogoma.data.model.SelectedFilters

@Composable
fun Filter(
    onFilterClick: (String) -> Unit,
    selectedFilters: SelectedFilters
){
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
    ){
        Row(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .fillMaxWidth()
                .height(62.2.dp)
                .padding(start = 24.dp, top = 4.dp, end = 24.dp, bottom = 17.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiary,
                icon = R.drawable.icon_tune,
                onCLick = {
                    onFilterClick("기본")
                },
            )
            FilterChip(
                text = selectedFilters.city?.displayText ?: "지역",
                isPoint = selectedFilters.city != null,
                onCLick = {
                    onFilterClick("지역")
                }
            )
            FilterChip(
                text = selectedFilters.marathonStatus?.displayText ?: "접수 상태",
                isPoint = selectedFilters.marathonStatus != null,
                onCLick = {
                    onFilterClick("접수 상태")
                }
            )
            FilterChip(
                text = if (!selectedFilters.courseTypeList.isNullOrEmpty()) {
                    selectedFilters.courseTypeList!!.joinToString(", ") { it.displayText }
                } else {
                    "종목"
                },
                isPoint = selectedFilters.courseTypeList?.isNotEmpty() == true,
                onCLick = {
                    onFilterClick("종목")
                }
            )
            FilterChip(
                text = selectedFilters.year?.displayText ?: "년도",
                isPoint = selectedFilters.year != null,
                onCLick = {
                    onFilterClick("년도")
                }
            )
            FilterChip(
                text = selectedFilters.month?.displayText ?: "월",
                isPoint = selectedFilters.month != null,
                onCLick = {
                    onFilterClick("월")
                }
            )
        }
    }
}