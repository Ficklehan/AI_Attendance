<template>
  <div class="task-edit-container page-inner" :class="{ 'has-sticky-submit': canShowSubmitBar }">
    <div class="image-compare-layout" :class="{ 'image-compare-layout--dock-open': previewDockOpen }">
      <div class="image-compare-layout__main">
    <PageShell :title="$t('taskEdit.title')" :subtitle="taskId" inline-subtitle>
      <template #meta>
        <div
          v-if="hasTaskWorkRegion"
          class="task-header-meta"
          :class="{ 'task-header-meta--warning': workRegionMismatch }"
        >
          <span class="task-header-meta__label">{{ $t('taskEdit.workRegionContextTitle') }}</span>
          <span class="task-header-meta__value">{{ taskWorkRegionLabel }}</span>
          <span class="task-header-meta__dot">·</span>
          <span class="task-header-meta__hint">
            {{ $t('taskEdit.workRegionContextSummary', {
              region: taskWorkRegionLabel,
              column: $t('taskEdit.countryField'),
              code: taskWorkRegionBannerCode || taskWorkRegionDisplayCode || taskWorkRegionCode || '—',
            }) }}
          </span>
          <a-tooltip overlay-class-name="task-header-meta-tooltip">
            <template #title>
              <ol class="task-header-meta__tooltip-steps">
                <li>{{ $t('taskEdit.workRegionContextStep1', { column: $t('taskEdit.countryField') }) }}</li>
                <li>{{ $t('taskEdit.workRegionContextStep2') }}</li>
              </ol>
            </template>
            <InfoCircleOutlined class="task-header-meta__info" />
          </a-tooltip>
          <a-tag v-if="workRegionMismatch" color="warning" class="task-header-meta__badge">
            {{ $t('taskEdit.workRegionMismatchBadge', { count: workRegionMismatchCount }) }}
          </a-tag>
        </div>
      </template>
      <template #extra>
        <a-space wrap>
          <a-tag v-if="task?.status === 'cancelled'" color="default">{{ $t('tasks.statusCancelled') }}</a-tag>
          <span class="record-count">{{ $t('tasks.totalRecords', { total: records.length }) }}</span>
          <a-button @click="$router.back()">
            <template #icon><UndoOutlined /></template>
            {{ $t('common.back') }}
          </a-button>
          <a-button
            v-if="canDeleteTask"
            danger
            :loading="deleting"
            @click="handleDeleteTask"
          >
            <template #icon><DeleteOutlined /></template>
            {{ $t('common.delete') }}
          </a-button>
          <a-button
            v-if="canDeleteTask"
            :loading="deleting"
            @click="handleReupload"
          >
            <template #icon><UploadOutlined /></template>
            {{ $t('taskEdit.reupload') }}
          </a-button>
          <a-button
            @click="handleExportExcel"
            :disabled="records.length === 0"
          >
            <template #icon><DownloadOutlined /></template>
            {{ $t('taskEdit.exportExcel') }}
          </a-button>
        </a-space>
      </template>
    </PageShell>

    <a-card :loading="loading" :bordered="false" class="edit-card surface-card">

      <a-alert
        v-if="task && task.status === 'confirmed' && task.syncStatus && task.syncStatus !== 'none'"
        :type="syncAlertType"
        show-icon
        class="sync-alert"
      >
        <template #message>
          <span class="sync-alert-message">
            <LoadingOutlined v-if="task.syncStatus === 'pending'" spin class="sync-alert-spin" />
            {{ syncAlertMessage }}
          </span>
        </template>
        <template v-if="task.syncError && task.syncStatus === 'sync_failed'" #description>
          {{ task.syncError }}
        </template>
        <template v-if="canRetrySync" #action>
          <a-button size="small" :loading="retryingSync" @click="handleRetrySync">
            {{ $t('taskEdit.syncRetry') }}
          </a-button>
        </template>
      </a-alert>

      <StatOverview
        v-if="records.length > 0"
        :items="statItems"
        single-row
        clickable
        :active-key="activeStatFilter"
        :filter-hint="$t('taskEdit.statFilterHint')"
        :clear-hint="$t('taskEdit.statFilterClearHint')"
        @select="onStatFilterSelect"
      />

      <div v-if="previewImagesList.length > 0" class="task-image-files">
        <span class="task-image-files__title">
          <FileImageOutlined />
          {{ $t('taskEdit.originalImage') }}
          <em>({{ previewImagesList.length }}{{ $t('tasks.images') }})</em>
        </span>
        <ul class="task-image-files__list">
          <li
            v-for="(url, idx) in previewImagesList"
            :key="`${url}-${idx}`"
            class="task-image-files__item"
            :class="{ 'task-image-files__item--active': previewDockOpen && previewCurrentIndex === idx }"
          >
            <button
              type="button"
              class="task-image-files__link"
              :title="$t('tasks.viewImage')"
              @click="openImagePreview(idx)"
            >
              <span class="task-image-files__name">{{ getFileName(url) }}</span>
            </button>
          </li>
        </ul>
      </div>

      <div class="edit-panel">
          <div class="edit-toolbar">
            <span class="edit-toolbar__title">{{ $t('taskEdit.editData') }}</span>
            <a-tooltip v-if="canShowSubmitBar" :title="$t('taskEdit.addManualRowHint')">
              <a-button
                type="dashed"
                size="small"
                class="edit-toolbar__add-row"
                @click="handleAddManualRecord"
              >
                <template #icon><PlusOutlined /></template>
                {{ $t('taskEdit.addManualRow') }}
              </a-button>
            </a-tooltip>
            <span class="duplicate-scope-label">{{ $t('taskEdit.duplicateScopeLabel') }}</span>
            <a-radio-group
              v-model:value="duplicateScope"
              size="small"
              class="edit-toolbar__scope"
              @change="handleDuplicateScopeChange"
            >
              <a-radio-button value="confirmed_only">{{ $t('taskEdit.duplicateScopeConfirmedOnly') }}</a-radio-button>
              <a-radio-button value="confirmed_and_processing">{{ $t('taskEdit.duplicateScopeConfirmedAndProcessing') }}</a-radio-button>
            </a-radio-group>
            <div class="edit-toolbar__spacer" />
            <div
              v-if="reviewIssuesChipVisible"
              class="edit-toolbar__issues"
              role="group"
              :aria-label="$t('taskEdit.reviewQuickView')"
            >
              <span class="edit-toolbar__issues-lead">
                <FilterOutlined class="edit-toolbar__issues-icon" />
                {{ $t('taskEdit.reviewQuickView') }}
              </span>
              <div class="edit-toolbar__issues-group">
              <a-tooltip
                v-if="submitValidationCount > 0 && !isConfirmedTask"
                :title="activeStatFilter === 'submitBlocked'
                  ? $t('taskEdit.reviewBlockedClearHint')
                  : $t('taskEdit.reviewBlockedHint')"
                :mouse-enter-delay="0"
                :mouse-leave-delay="0"
              >
                <button
                  type="button"
                  class="edit-toolbar__issues-filter"
                  :class="{ 'is-active': activeStatFilter === 'submitBlocked' }"
                  :aria-pressed="activeStatFilter === 'submitBlocked'"
                  @click="onStatFilterSelect('submitBlocked')"
                >
                  <span>{{ $t('taskEdit.reviewBlockedShort', { count: submitValidationCount }) }}</span>
                  <CloseOutlined
                    v-if="activeStatFilter === 'submitBlocked'"
                    class="edit-toolbar__issues-clear"
                    aria-hidden="true"
                  />
                </button>
              </a-tooltip>
              <a-tooltip
                v-if="attentionAlertCount > 0"
                :title="activeStatFilter === 'attention'
                  ? $t('taskEdit.reviewAttentionClearHint')
                  : $t('taskEdit.reviewAttentionHint')"
                :mouse-enter-delay="0"
                :mouse-leave-delay="0"
              >
                <button
                  type="button"
                  class="edit-toolbar__issues-filter"
                  :class="{ 'is-active': activeStatFilter === 'attention' }"
                  :aria-pressed="activeStatFilter === 'attention'"
                  @click="onStatFilterSelect('attention')"
                >
                  <span>{{ $t('taskEdit.reviewAttentionShort', { count: attentionAlertCount }) }}</span>
                  <CloseOutlined
                    v-if="activeStatFilter === 'attention'"
                    class="edit-toolbar__issues-clear"
                    aria-hidden="true"
                  />
                </button>
              </a-tooltip>
              </div>
            </div>
            <TableColumnSettings
              :columns="configurableColumns"
              :hidden-keys="hiddenKeys"
              :frozen-keys="frozenKeys"
              @update:hidden-keys="setHiddenKeys"
              @update:frozen-keys="setFrozenKeys"
              @show-all="showAllColumns"
              @clear-freeze="clearFrozenKeys"
            />
          </div>

          <a-table 
            :columns="columns" 
            :data-source="visibleTableRecords" 
            :pagination="false"
            :scroll="{ x: scrollX }"
            :row-key="getRowKey"
            :row-class-name="getRowClassName"
            :expanded-row-keys="expandedDuplicateRowKeys"
            :expand-icon="hiddenExpandIcon"
            :show-expand-column="false"
            :row-expandable="(record) => !!getDuplicateMeta(record)"
            :custom-row="customTableRow"
            @expand="handleTableExpand"
            size="small"
            class="edit-table rich-table-header"
          >
            <template #headerCell="{ column }">
              <TableSortableHeader
                v-if="!isAuxHeaderColumn(column)"
                :column="column"
                :title="formatHeaderTitle(column.title)"
                :compact="isNarrowHeaderColumn(column)"
                :micro="isMicroHeaderColumn(column)"
                :hint="resolveHeaderHint(column)"
                :resizable="isColumnResizable(column)"
                @sort="onSorterToggle"
                @resize-start="(event) => startColumnResize(column, event)"
              >
                <template #extra>
                  <TableHeaderFilter
                    v-if="column.searchField"
                    :title="formatHeaderTitle(column.title)"
                    :open="activeHeaderFilterField === column.searchField"
                    :active="isHeaderFilterActive(column.searchField)"
                    @openChange="(open) => handleHeaderPopoverOpen(column, open)"
                    @reset="clearHeaderFilter"
                    @apply="applyHeaderFilter"
                  >
                    <template #field>
                      <FieldFilterControl
                        v-model:model-value="headerFilterDraft"
                        :filter-type="resolveColumnFilterType(column)"
                        :options="resolveColumnFilterOptions(column, t, paysCountrySelectOptions)"
                        class="table-header-filter-panel__input"
                        @submit="applyHeaderFilter"
                      />
                    </template>
                  </TableHeaderFilter>
                </template>
              </TableSortableHeader>
              <span v-else></span>
            </template>
            <template #expandedRowRender="{ record }">
              <div v-if="getDuplicateMeta(record)" class="duplicate-expanded">
                <div class="duplicate-expanded-title">{{ $t('taskEdit.duplicateDetailTitle') }}</div>
                <div class="duplicate-expanded-list">
                  <table class="duplicate-expanded-table">
                    <thead>
                      <tr>
                        <th>{{ $t('taskEdit.sourceTask') }}</th>
                        <th>{{ $t('taskEdit.pageNumber') }}</th>
                        <th>{{ $t('taskEdit.workerNumber') }}</th>
                        <th>{{ $t('taskEdit.name') }}</th>
                        <th>{{ $t('taskEdit.countryField') }}</th>
                        <th>{{ $t('taskEdit.warehouse') }}</th>
                        <th>{{ $t('taskEdit.date') }}</th>
                        <th>{{ $t('taskEdit.agency') }}</th>
                        <th>{{ $t('taskEdit.shift') }}</th>
                        <th>{{ $t('taskEdit.arrival') }}</th>
                        <th>{{ $t('taskEdit.departure') }}</th>
                        <th>{{ $t('taskEdit.breakTime') }}</th>
                        <th>{{ $t('taskEdit.signature') }}</th>
                        <th>{{ $t('taskEdit.observations') }}</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr
                        v-for="item in getDuplicateMeta(record).members"
                        :key="item.rowKey"
                      >
                        <td>{{ item.sourceTaskId || taskId }}</td>
                        <td>{{ item.pageNum || item.PAGE_NUM || '-' }}</td>
                        <td>{{ item.NO || '-' }}</td>
                        <td>{{ item.displayName || '-' }}</td>
                        <td>{{ displayPaysField(item) || '-' }}</td>
                        <td>{{ item.Entrepot || '-' }}</td>
                        <td>{{ item.Date || '-' }}</td>
                        <td>{{ item.AGENCE_INTERIMAIRE || '-' }}</td>
                        <td>{{ formatDuplicateCell(item.HORAIRES_DU_TRAVAIL) }}</td>
                        <td>{{ formatDuplicateCell(item.ARRIVEE) }}</td>
                        <td>{{ formatDuplicateCell(item.DEPAR) }}</td>
                        <td>{{ formatDuplicateCell(item.PAUSE, 'pause') }}</td>
                        <td>{{ formatDuplicateCell(item.SIGNATURE) }}</td>
                        <td>{{ formatDuplicateCell(item.Observations) }}</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>
            </template>
            <template #bodyCell="{ column, record, index }">
              <template v-if="column.key === 'anomalyReasons'">
                <div v-if="hasAnomalyColumnContent(record)" class="recognition-note-list">
                  <div
                    v-for="(item, noteIdx) in getRecognitionNoteItems(record)"
                    :key="item.key"
                    class="recognition-note-list__item"
                    :class="`recognition-note-list__item--${item.tone || 'default'}`"
                  >
                    <span class="recognition-note-list__index" aria-hidden="true">{{ noteIdx + 1 }}</span>
                    <a-popover
                      v-if="item.showCalibrationHistory"
                      placement="bottomLeft"
                      trigger="click"
                      overlay-class-name="calibration-history-popover"
                    >
                      <template #title>{{ $t('calibration.historyTitle') }}</template>
                      <template #content>
                        <div class="calibration-popover-list">
                          <div
                            v-for="(entry, hIdx) in getCalibrationHistoryReversed(record)"
                            :key="hIdx"
                            class="calibration-popover-item"
                          >
                            <div class="calibration-popover-meta">
                              <span>{{ entry.byName || entry.by }}</span>
                              <span>{{ formatCalibrationTime(entry.at) }}</span>
                            </div>
                            <div v-if="entry.reason" class="calibration-popover-reason">{{ entry.reason }}</div>
                            <ul class="calibration-popover-changes">
                              <li
                                v-for="line in formatCalibrationHistoryChanges(entry)"
                                :key="line.field"
                              >
                                {{ line.label }}: <span class="from">{{ line.from }}</span> → <span class="to">{{ line.to }}</span>
                              </li>
                            </ul>
                          </div>
                        </div>
                      </template>
                      <button type="button" class="recognition-note-list__text recognition-note-list__text--link">
                        {{ item.text }}
                      </button>
                    </a-popover>
                    <span v-else class="recognition-note-list__text">{{ item.text }}</span>
                  </div>
                </div>
                <RowStrikeText v-else :muted="isRowMuted(record)" cell-class="cell-muted">-</RowStrikeText>
              </template>
              <template v-else-if="column.key === 'PAGE_NUM'">
                <div class="page-num-with-line">
                  <span class="record-line-no-tag">{{ recordLineNo(record) }}</span>
                  <a-input
                    v-if="isFieldEditable(record, 'PAGE_NUM')"
                    v-model:value="record.PAGE_NUM"
                    size="small"
                    :bordered="false"
                    :class="fieldInputClass(record, 'PAGE_NUM')"
                    :style="mutedStrikeStyle(record)"
                  />
                  <RowStrikeText v-else :muted="isRowMuted(record)" :cell-class="fieldTextClass(record, 'PAGE_NUM')">{{ displayFieldValue(record.PAGE_NUM || record.pageNum) }}</RowStrikeText>
                </div>
              </template>
              <template v-else-if="column.key === 'EMPLOYEE_NO'">
                <RowStrikeText :muted="isRowMuted(record)" :cell-class="fieldTextClass(record, 'EMPLOYEE_NO')">{{ displayRecordField(record, 'EMPLOYEE_NO') }}</RowStrikeText>
              </template>
              <template v-else-if="column.key === 'NO'">
                <a-input v-if="isFieldEditable(record, 'NO')" v-model:value="record.NO" size="small" :class="fieldInputClass(record, 'NO')" :style="mutedStrikeStyle(record)" :bordered="false" :placeholder="fieldUnreadablePlaceholder(record, 'NO')" @change="onReadableFieldChange(record, 'NO')" />
                <RowStrikeText v-else :muted="isRowMuted(record)" :cell-class="fieldTextClass(record, 'NO')">{{ displayRecordField(record, 'NO') }}</RowStrikeText>
              </template>
              <template v-else-if="column.key === 'Pays'">
                <a-tooltip :title="paysFieldLockedHint">
                  <a-select
                    :value="resolveRecordPaysSelectCode(record)"
                    :options="paysCountrySelectOptions"
                    size="small"
                    disabled
                    :bordered="false"
                    class="pays-country-select"
                    :class="fieldTextClass(record, 'Pays')"
                    :style="mutedStrikeStyle(record)"
                  />
                </a-tooltip>
              </template>
              <template v-else-if="column.key === 'Entrepot'">
                <div class="field-with-change-hint">
                  <a-input v-if="isFieldEditable(record, 'Entrepot')" v-model:value="record.Entrepot" size="small" :class="fieldInputClass(record, 'Entrepot')" :style="mutedStrikeStyle(record)" :bordered="false" :placeholder="fieldUnreadablePlaceholder(record, 'Entrepot')" @focus="onCalibratableFieldFocusHandler(record)" @change="onReadableFieldChange(record, 'Entrepot')" />
                  <RowStrikeText v-else :muted="isRowMuted(record)" :cell-class="fieldTextClass(record, 'Entrepot')">{{ displayRecordField(record, 'Entrepot') }}</RowStrikeText>
                  <FieldChangeHint :hint="getFieldChangeHint(record, 'Entrepot')" />
                </div>
              </template>
              <template v-else-if="column.key === 'NOM_PRENOM'">
                <div class="name-cell field-with-change-hint">
                  <a-input
                    v-if="isFieldEditable(record, 'NOM_PRENOM')"
                    v-model:value="record.NOM_PRENOM"
                    size="small"
                    :class="fieldInputClass(record, 'NOM_PRENOM')"
                    :style="mutedStrikeStyle(record)"
                    :bordered="false"
                    :placeholder="fieldUnreadablePlaceholder(record, 'NOM_PRENOM')"
                    @focus="onCalibratableFieldFocusHandler(record)"
                    @change="onReadableFieldChange(record, 'NOM_PRENOM')"
                  />
                  <RowStrikeText v-else :muted="isRowMuted(record)" :cell-class="fieldTextClass(record, 'NOM_PRENOM')">{{ displayRecordField(record, 'NOM_PRENOM') }}</RowStrikeText>
                  <FieldChangeHint :hint="getFieldChangeHint(record, 'NOM_PRENOM')" />
                  <div v-if="getDuplicateMeta(record)" class="duplicate-tools">
                    <a-tag color="gold" size="small">{{ $t('taskEdit.duplicateTag') }}</a-tag>
                    <a-button type="link" size="small" class="duplicate-link" @click="toggleDuplicateExpand(record)">
                      {{ expandedDuplicateRowKeys.includes(record._rowKey) ? $t('taskEdit.collapseDetail') : $t('taskEdit.expandDetail') }}
                    </a-button>
                    <a-button
                      v-if="!record._duplicateConfirmedUnique"
                      type="link"
                      size="small"
                      class="duplicate-link"
                      @click="confirmNotDuplicate(record)"
                    >
                      {{ $t('taskEdit.notDuplicate') }}
                    </a-button>
                  </div>
                </div>
              </template>
              <template v-else-if="column.key === 'AGENCE_INTERIMAIRE'">
                <div class="field-with-change-hint">
                  <a-input v-if="isFieldEditable(record, 'AGENCE_INTERIMAIRE')" v-model:value="record.AGENCE_INTERIMAIRE" size="small" :class="fieldInputClass(record, 'AGENCE_INTERIMAIRE')" :style="mutedStrikeStyle(record)" :bordered="false" :placeholder="fieldUnreadablePlaceholder(record, 'AGENCE_INTERIMAIRE')" @focus="onCalibratableFieldFocusHandler(record)" @change="onReadableFieldChange(record, 'AGENCE_INTERIMAIRE')" />
                  <RowStrikeText v-else :muted="isRowMuted(record)" :cell-class="fieldTextClass(record, 'AGENCE_INTERIMAIRE')">{{ displayRecordField(record, 'AGENCE_INTERIMAIRE') }}</RowStrikeText>
                  <FieldChangeHint :hint="getFieldChangeHint(record, 'AGENCE_INTERIMAIRE')" />
                </div>
              </template>
              <template v-else-if="column.key === 'HORAIRES_DU_TRAVAIL'">
                <div class="format-field-cell" :id="fieldCellDomId(record, 'HORAIRES_DU_TRAVAIL')">
                  <a-tooltip v-if="isFieldEditable(record, 'HORAIRES_DU_TRAVAIL')" :title="fieldFormatTooltip(record,'HORAIRES_DU_TRAVAIL')">
                    <a-input
                      v-model:value="record.HORAIRES_DU_TRAVAIL"
                      size="small"
                      :class="fieldInputClass(record, 'HORAIRES_DU_TRAVAIL')"
                      :style="mutedStrikeStyle(record)"
                      :bordered="false"
                      :placeholder="fieldCellPlaceholder(record, 'HORAIRES_DU_TRAVAIL')"
                      @focus="onCalibratableFieldFocusHandler(record)"
                      @change="onReadableFieldChange(record, 'HORAIRES_DU_TRAVAIL')"
                      @pressEnter="(e) => onFormatFieldPressEnter(record, 'HORAIRES_DU_TRAVAIL', e)"
                      @blur="() => onFormatFieldBlur(record, 'HORAIRES_DU_TRAVAIL')"
                    />
                  </a-tooltip>
                  <RowStrikeText v-else :muted="isRowMuted(record)" :cell-class="fieldTextClass(record, 'HORAIRES_DU_TRAVAIL')">{{ displayRecordField(record, 'HORAIRES_DU_TRAVAIL') }}</RowStrikeText>
                  <FieldChangeHint :hint="getFieldChangeHint(record, 'HORAIRES_DU_TRAVAIL')" />
                  <div v-if="showFormatHintBelow(record, 'HORAIRES_DU_TRAVAIL')" class="format-hint-below">{{ fieldFormatTooltip(record,'HORAIRES_DU_TRAVAIL') }}</div>
                </div>
              </template>
              <template v-else-if="column.key === 'Date'">
                <div class="format-field-cell" :id="fieldCellDomId(record, 'Date')">
                  <a-tooltip v-if="isFieldEditable(record, 'Date')" :title="fieldFormatTooltip(record,'Date')">
                    <a-date-picker
                      :value="datePickerValue(record.Date)"
                      size="small"
                      class="task-edit-date-picker"
                      :class="fieldInputClass(record, 'Date')"
                      :style="mutedStrikeStyle(record)"
                      format="YYYY-MM-DD"
                      value-format="YYYY-MM-DD"
                      :bordered="false"
                      :allow-clear="true"
                      :placeholder="dateFieldPlaceholder(record)"
                      @focus="onCalibratableFieldFocusHandler(record)"
                      @update:value="(v) => onDatePickerUpdate(record, v)"
                    />
                  </a-tooltip>
                  <RowStrikeText v-else :muted="isRowMuted(record)" :cell-class="fieldTextClass(record, 'Date')">{{ displayRecordField(record, 'Date') }}</RowStrikeText>
                  <FieldChangeHint :hint="getFieldChangeHint(record, 'Date')" />
                  <div v-if="showFormatHintBelow(record, 'Date')" class="format-hint-below">{{ fieldFormatTooltip(record,'Date') }}</div>
                </div>
              </template>
              <template v-else-if="column.key === 'ARRIVEE'">
                <div class="format-field-cell" :id="fieldCellDomId(record, 'ARRIVEE')">
                  <a-tooltip v-if="isFieldEditable(record, 'ARRIVEE')" :title="fieldFormatTooltip(record,'ARRIVEE')">
                    <ClockTimeField
                      :value="record.ARRIVEE"
                      size="small"
                      embedded
                      :bordered="false"
                      :input-class="fieldInputClass(record, 'ARRIVEE')"
                      :input-style="mutedStrikeStyle(record)"
                      :placeholder="fieldCellPlaceholder(record, 'ARRIVEE')"
                      @focus="onCalibratableFieldFocusHandler(record)"
                      @update:value="(v) => onClockTimeUpdate(record, 'ARRIVEE', v)"
                      @input="() => onTimeFieldInput(record, 'ARRIVEE')"
                      @commit="() => onFormatFieldBlur(record, 'ARRIVEE')"
                    />
                  </a-tooltip>
                  <RowStrikeText v-else :muted="isRowMuted(record)" :cell-class="fieldTextClass(record, 'ARRIVEE')">{{ displayRecordField(record, 'ARRIVEE') }}</RowStrikeText>
                  <FieldChangeHint
                    :hint="getFieldChangeHint(record, 'ARRIVEE')"
                    show-restore
                    @restore="restoreFieldOriginal(record, 'ARRIVEE')"
                  />
                  <div v-if="showSameTimeHint(record, 'ARRIVEE')" class="format-hint-below format-same-time-hint">
                    {{ sameTimeHintText(record) }}
                  </div>
                  <div v-else-if="showFormatHintBelow(record, 'ARRIVEE')" class="format-hint-below">{{ fieldFormatTooltip(record,'ARRIVEE') }}</div>
                </div>
              </template>
              <template v-else-if="column.key === 'DEPAR'">
                <div class="format-field-cell" :id="fieldCellDomId(record, 'DEPAR')">
                  <a-tooltip v-if="isFieldEditable(record, 'DEPAR')" :title="fieldFormatTooltip(record,'DEPAR')">
                    <ClockTimeField
                      :value="record.DEPAR"
                      size="small"
                      embedded
                      :bordered="false"
                      :input-class="fieldInputClass(record, 'DEPAR')"
                      :input-style="mutedStrikeStyle(record)"
                      :placeholder="fieldCellPlaceholder(record, 'DEPAR')"
                      @focus="onCalibratableFieldFocusHandler(record)"
                      @update:value="(v) => onClockTimeUpdate(record, 'DEPAR', v)"
                      @input="() => onTimeFieldInput(record, 'DEPAR')"
                      @commit="() => onFormatFieldBlur(record, 'DEPAR')"
                    />
                  </a-tooltip>
                  <RowStrikeText v-else :muted="isRowMuted(record)" :cell-class="fieldTextClass(record, 'DEPAR')">{{ displayRecordField(record, 'DEPAR') }}</RowStrikeText>
                  <FieldChangeHint
                    :hint="getFieldChangeHint(record, 'DEPAR')"
                    show-restore
                    @restore="restoreFieldOriginal(record, 'DEPAR')"
                  />
                  <div v-if="showSameTimeHint(record, 'DEPAR')" class="format-hint-below format-same-time-hint">
                    {{ sameTimeHintText(record) }}
                  </div>
                  <div v-else-if="showFormatHintBelow(record, 'DEPAR')" class="format-hint-below">{{ fieldFormatTooltip(record,'DEPAR') }}</div>
                </div>
              </template>
              <template v-else-if="column.key === 'PAUSE'">
                <div class="field-with-change-hint">
                  <a-input-number v-if="isFieldEditable(record, 'PAUSE')" v-model:value="record.PAUSE" size="small" :class="fieldInputClass(record, 'PAUSE')" :style="mutedStrikeStyle(record)" :bordered="false" :controls="false" :min="0" :precision="0" style="width: 100%" @focus="onCalibratableFieldFocusHandler(record)" @blur="() => normalizeRecordPauseOnBlur(record)" />
                  <RowStrikeText v-else :muted="isRowMuted(record)" :cell-class="fieldTextClass(record, 'PAUSE')">{{ formatPauseDisplay(record.PAUSE) }}</RowStrikeText>
                  <FieldChangeHint :hint="getFieldChangeHint(record, 'PAUSE')" />
                </div>
              </template>
              <template v-else-if="column.key === 'SIGNATURE'">
                <a-select
                  v-if="isFieldEditable(record, 'SIGNATURE')"
                  v-model:value="record.SIGNATURE"
                  size="small"
                  :bordered="false"
                  class="signature-mark-select"
                  popup-class-name="task-edit-select-dropdown-sm"
                  :placeholder="$t('taskEdit.signature')"
                  allow-clear
                >
                  <a-select-option value="未签字">{{ $t('recognition.marks.unsigned') }}</a-select-option>
                  <a-select-option value="已签字">{{ $t('recognition.marks.signed') }}</a-select-option>
                </a-select>
                <a-tag
                  v-else
                  :color="getSignatureMarkColor(getDisplaySignature(record.SIGNATURE, record))"
                  class="signature-mark-tag"
                  :class="rowMutedStrikeClass(record)"
                  :style="mutedStrikeStyle(record)"
                >
                  {{ translateSignatureMark(getDisplaySignature(record.SIGNATURE, record), t) }}
                </a-tag>
              </template>
              <template v-else-if="column.key === 'Observations'">
                <a-input v-if="isFieldEditable(record, 'Observations')" v-model:value="record.Observations" size="small" :class="fieldInputClass(record, 'Observations')" :style="mutedStrikeStyle(record)" :bordered="false" :placeholder="fieldUnreadablePlaceholder(record, 'Observations')" @change="onReadableFieldChange(record, 'Observations')" />
                <RowStrikeText v-else :muted="isRowMuted(record)" :cell-class="fieldTextClass(record, 'Observations')">{{ displayRecordField(record, 'Observations') }}</RowStrikeText>
              </template>
              <template v-else-if="column.key === 'ExceptionType'">
                <div :id="fieldCellDomId(record, 'ExceptionType')" class="exception-type-cell">
                  <a-tooltip
                    v-if="isRecordEditable(record) && !isExceptionTypeExempt(record, isAbsentRow)"
                    :title="exceptionTypeDisabledHint(record)"
                    placement="left"
                    :mouse-enter-delay="0"
                    :mouse-leave-delay="0"
                    :open="isExceptionTypeSelectDisabled(record, exceptionTypeDeps) ? undefined : false"
                  >
                    <div
                      class="exception-type-stack"
                      :class="{
                        'is-collapsed': isExceptionTypePickerCollapsed(record),
                        'is-pending': isExceptionTypeMissingForSubmit(record, exceptionTypeDeps),
                      }"
                      role="radiogroup"
                      :aria-label="$t('taskEdit.mark')"
                      :aria-expanded="isExceptionTypePickerCollapsed(record) ? 'false' : 'true'"
                      :aria-disabled="isExceptionTypeSelectDisabled(record, exceptionTypeDeps) ? 'true' : 'false'"
                    >
                      <a-tooltip
                        v-for="opt in visibleExceptionTypeOptions(record)"
                        :key="opt.value"
                        :title="opt.title"
                        placement="left"
                        :mouse-enter-delay="0"
                        :mouse-leave-delay="0"
                        :open="isExceptionTypeSelectDisabled(record, exceptionTypeDeps) ? false : undefined"
                      >
                        <button
                          type="button"
                          role="radio"
                          class="exception-type-stack__opt"
                          :class="[
                            `exception-type-stack__opt--${opt.value}`,
                            {
                              'is-active': normalizeExceptionType(record.ExceptionType) === opt.value,
                              'is-disabled': isExceptionTypeSelectDisabled(record, exceptionTypeDeps),
                            },
                          ]"
                          :aria-checked="normalizeExceptionType(record.ExceptionType) === opt.value"
                          :aria-label="opt.title"
                          :disabled="isExceptionTypeSelectDisabled(record, exceptionTypeDeps)"
                          @click="handleExceptionTypeSelect(record, opt.value)"
                        >
                          <span
                            v-if="normalizeExceptionType(record.ExceptionType) === opt.value"
                            class="exception-type-stack__check"
                            aria-hidden="true"
                          >✓</span>
                          <span class="exception-type-stack__label">{{ opt.label }}</span>
                        </button>
                      </a-tooltip>
                    </div>
                  </a-tooltip>
                  <RowStrikeText v-else :muted="isRowMuted(record)" cell-class="cell-text exception-type-readonly">
                    {{ exceptionTypeDisplayLabel(record) }}
                  </RowStrikeText>
                </div>
              </template>
              <template v-else-if="column.key === 'workHours'">
                <RowStrikeText :muted="isRowMuted(record)" cell-class="work-hours">{{ calculateWorkHours(record) }}</RowStrikeText>
              </template>
              <template v-else-if="column.key === 'action'">
                <div
                  class="table-action-cell"
                  :class="(isConfirmedTask && canCalibrateRecord && !record.isDeleted)
                    ? 'table-action-cell--icons-mixed'
                    : 'table-action-cell--icons table-action-cell--icons-1'"
                >
                  <span
                    v-if="isConfirmedTask && canCalibrateRecord && !record.isDeleted"
                    class="table-action-cell__slot"
                  >
                    <a-button
                      type="link"
                      size="small"
                      @click="openCalibration(record)"
                    >
                      {{ $t('calibration.action') }}
                    </a-button>
                  </span>
                  <span class="table-action-cell__slot">
                    <a-tooltip
                      v-if="!(isConfirmedTask && canCalibrateRecord && !record.isDeleted)"
                      :title="(record?.isDeleted || isAbsentRow(record)) ? $t('taskEdit.restore') : $t('common.delete')"
                      :mouse-enter-delay="0"
                      :mouse-leave-delay="0"
                    >
                      <a-button
                        v-if="record?.isDeleted || isAbsentRow(record)"
                        type="link"
                        size="small"
                        @click="toggleDelete(record, index)"
                        class="action-btn restore-btn"
                      >
                        <template #icon><UndoOutlined /></template>
                      </a-button>
                      <a-button
                        v-else
                        type="link"
                        size="small"
                        danger
                        @click="toggleDelete(record, index)"
                        class="action-btn delete-btn"
                      >
                        <template #icon><DeleteOutlined /></template>
                      </a-button>
                    </a-tooltip>
                  </span>
                </div>
              </template>
            </template>
          </a-table>

          <div
            v-if="hasMoreTableRows"
            ref="tableLoadMoreSentinel"
            class="table-scroll-load-more"
          >
            <a-spin v-if="loadingMoreTableRows" size="small" />
            <span v-else class="table-scroll-load-more__text">
              {{ $t('taskEdit.scrollLoadMore', { loaded: visibleTableRecords.length, total: tableRecords.length }) }}
            </span>
          </div>
        </div>
    </a-card>

    <Teleport to="body">
      <div v-if="canShowSubmitBar" class="task-edit-submit-bar">
        <div class="task-edit-submit-bar__inner">
          <a-button
            type="primary"
            :loading="submitting"
            @click="handleSubmit"
            size="large"
          >
            {{ $t('taskEdit.submitConfirm') }}
          </a-button>
          <a-button @click="$router.back()" size="large">{{ $t('common.cancel') }}</a-button>
        </div>
      </div>
    </Teleport>
      </div>

      <ImageCompareDockShell
        v-if="previewDockOpen"
        v-model:open="previewDockOpen"
        v-model:index="previewCurrentIndex"
        :images="previewImagesList"
        :title="$t('tasks.imagePreview')"
        @fullscreen="openPreviewFullscreen"
      />
    </div>
  </div>
  
  <ImagePreviewModal
    v-model:open="previewFullscreenOpen"
    v-model:index="previewCurrentIndex"
    :images="previewImagesList"
  />

  <RecordCalibrationModal
    v-model:open="calibrationVisible"
    :record="calibrationRecord"
    :submitting="calibrationSubmitting"
    @submit="handleCalibrationSubmit"
  />

  <TaskDeleteModal
    v-model:open="deleteModalOpen"
    :task-id="taskId"
    :confirmed="task?.status === 'confirmed'"
    :submitting="deleting"
    @submit="handleDeleteSubmit"
  />
</template>

<script setup>
import { ref, computed, shallowRef, onMounted, onUnmounted, watch, h, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { message, Modal as aModal } from 'ant-design-vue'
import { DeleteOutlined, UndoOutlined, CloseOutlined, FileImageOutlined, UploadOutlined, DownloadOutlined, LoadingOutlined, PlusOutlined, InfoCircleOutlined, FilterOutlined } from '@ant-design/icons-vue'
import { getTaskDetail, getTaskProgress, confirmTask, saveTaskDraft, deleteTask, retryFeishuSync, calibrateTaskRecord } from '@/api/task'
import { useAuthStore } from '@/stores/auth'
import { fileNameFromImageUrl } from '@/utils/imageUrl'
import StatOverview from '@/components/StatOverview.vue'
import PageShell from '@/components/PageShell.vue'
import TruncatedTag from '@/components/TruncatedTag.vue'
import RowStrikeText from '@/components/RowStrikeText.vue'
import ClockTimeField from '@/components/ClockTimeField.vue'
import FieldChangeHint from '@/components/FieldChangeHint.vue'
import ImagePreviewModal from '@/components/ImagePreviewModal.vue'
import ImageCompareDockShell from '@/components/ImageCompareDockShell.vue'
import { useTaskImagePreview } from '@/composables/useTaskImagePreview'
import RecordCalibrationModal from '@/components/RecordCalibrationModal.vue'
import TaskDeleteModal from '@/components/TaskDeleteModal.vue'
import TableSortableHeader from '@/components/TableSortableHeader.vue'
import TableHeaderFilter from '@/components/TableHeaderFilter.vue'
import { useTableColumnSort } from '@/composables/useTableColumnSort'
import { useAutoSizedColumns } from '@/composables/useAutoSizedColumns'
import { useTableColumnResize } from '@/composables/useTableColumnResize'
import { sumTableScrollX } from '@/utils/tableAutoColumns'
import { translateApiError } from '@/utils/translateError'
import { currentExportLocale } from '@/utils/exportLocale'
import { getToken } from '@/utils/auth'
import { getCachedWorkingCountry } from '@/utils/countryHeader'
import { useCountryStore } from '@/stores/country'
import {
  resolveTaskWorkRegionCode,
  resolveTaskWorkRegionDisplayCountryCode,
  resolveTaskWorkRegionBannerCode,
  resolveTaskWorkRegionLabel,
  resolveTaskWorkRegionBindingCode,
  resolveManualRecordCountryCode,
  resolveRecordPaysSelectCode as resolveRecordPaysSelectCodeCore,
  resolveTaskWorkRegionHistoricalCountryCode,
  resolveTaskNightShiftCountryCode,
  isTaskCountryFollowingWorking as checkTaskCountryFollowingWorking,
} from '@/utils/taskWorkRegion'
import { formatPaysFieldDisplay } from '@/utils/countryLabels'
import { resolveCountryCodeFromPays, normalizeCountryCode } from '@/utils/countryCatalog'
import { applyWorkingCountryPays, defaultPaysLabel, sanitizeEntrepot } from '@/utils/countryDefaults'
import {
  translateSmartMark,
  computeSignatureMark,
  getDisplaySignature,
  translateSignatureMark,
  getSignatureMarkColor,
  refreshNightShiftInSmartMark,
  getRawSmartMark,
} from '@/utils/recognitionLabels'
import {
  EXCEPTION_TYPE,
  EXCEPTION_TYPE_VALUES,
  EXCEPTION_TYPE_I18N_KEYS,
  EXCEPTION_TYPE_SHORT_I18N_KEYS,
  SNAPSHOT_FIELD_KEYS,
  CALIBRATION_HIGHLIGHT_FIELDS,
  ensureExceptionType,
  isExceptionTypeSelectDisabled,
  isExceptionTypeExempt,
  canEditRequiredFields,
  onExceptionTypeChange,
  onCalibratableFieldFocus,
  onCalibratableFieldChange,
  normalizeExceptionType,
  ensureAiBaseline,
  isExceptionTypeMissingForSubmit,
} from '@/utils/exceptionType'
import { hasRequiredMissing } from '@/utils/requiredRecordFields'
import FieldFilterControl from '@/components/FieldFilterControl.vue'
import TableColumnSettings from '@/components/TableColumnSettings.vue'
import { useColumnFreeze } from '@/composables/useColumnFreeze'
import {
  emptyFilterValue,
  isFilterActive,
  matchRecordByFilter,
  resolveColumnFilterOptions,
  resolveColumnFilterType,
  serializeFilterValue,
} from '@/utils/fieldFilterValue'
import { buildRecognitionTableColumns } from '@/utils/recognitionTableColumns'
import {
  hasManualCalibration,
  parseCalibrationHistory,
  formatHistoryChanges,
} from '@/utils/calibrationHistory'
import { FIELD_LABEL_KEYS } from '@/constants/calibratableFields'
import { API_BASE_PATH } from '@/constants/apiBase'
import { getConfirmValidationConfig as fetchConfirmValidationConfig } from '@/api/config'
import { displayFieldValue, isFieldUnreadable, clearFieldUnreadable, isPlaceholderValue, prepareRecordPlaceholders, sanitizeFieldValue, sanitizeRecordPlaceholders, stripRecordMetadata } from '@/utils/fieldPlaceholder'
import { startAdaptivePoll } from '@/utils/adaptivePoll'
import { isAbsentRow } from '@/utils/recordDisplay'
import { resolveTaskRecordsJson } from '@/utils/taskRecordPayload'
import { useTaskEditDuplicates } from '@/composables/useTaskEditDuplicates'
import { stripSerialSuffix } from '@/utils/duplicateCheck'
import { useTaskEditConfirmValidation } from '@/composables/useTaskEditConfirmValidation'
import { useTaskEditRecordDisplay } from '@/composables/useTaskEditRecordDisplay'
import { getFormatHintKeys, isFormatHintField } from '@/utils/fieldFormatHints'
import { isArrivalDepartureSameTime } from '@/utils/recordFieldFormatRules'
import {
  normalizeWorkerNo,
  normalizePersonName,
  normalizeLabelText,
} from '@/utils/recognizedTextNormalizer'
import { isValidCanonicalDate, normalizeDate } from '@/utils/recognizedDateNormalizer'
import { loadNightShiftRules } from '@/utils/nightShiftRules'
import { isNonTimeFieldLabel, normalizeClockTime, normalizeShiftSchedule } from '@/utils/recognizedTimeNormalizer'
import { createManualTaskRecord } from '@/utils/manualTaskRecord'

const { t, locale } = useI18n()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const taskId = computed(() => route.params.taskId)
const TABLE_SCROLL_BATCH = 50
const visibleRowCount = ref(TABLE_SCROLL_BATCH)
const loadingMoreTableRows = ref(false)
const tableLoadMoreSentinel = ref(null)
const columnsLocked = ref(false)
const lockedSizedColumns = shallowRef([])
let tableLoadMoreObserver = null
const loading = ref(false)
const submitting = ref(false)
const retryingSync = ref(false)
const deleting = ref(false)
let stopSyncPoll = null
const records = ref([])

/** ant-design-vue Table：expandIcon 返回 null 会在路由卸载时触发 vnode 为 null 的崩溃 */
const hiddenExpandIcon = () =>
  h('span', { class: 'task-edit-expand-icon-placeholder', style: { display: 'none' }, 'aria-hidden': 'true' })

const {
  expandedDuplicateRowKeys,
  duplicateRefreshing,
  duplicateScope,
  getDuplicateMeta,
  fetchConfirmedDuplicateHints,
  scheduleDuplicateRecheck,
  handleDuplicateScopeChange,
  toggleDuplicateExpand,
  handleTableExpand,
  confirmNotDuplicate,
  markNameManuallyEdited,
} = useTaskEditDuplicates(taskId, records)

const {
  confirmRequiredFields,
  applyConfirmValidationConfig,
  isConfiguredRequiredField,
  isRequiredFieldEmpty,
  isFormatFieldInvalid,
  requiredInputClass,
  requiredTextClass,
  validateBeforeConfirm,
  collectConfirmValidationIssues,
  collectSubmitValidationIssues,
  countSubmitValidationLines,
  showConfirmValidationModal,
} = useTaskEditConfirmValidation()

const {
  statItems,
  getRecordMarkTags,
  getDisplaySmartMark,
  getSmartMarkDisplay,
  getRecordAnomalyReasons,
  getRecognitionNoteItems,
  getRecordAnomalyGroups,
  getRecordShiftVarianceSentence,
  getFieldChangeHint,
  hasAnomalyColumnContent,
  getRowClassName: getBaseRowClassName,
  getMarkColor,
  getRowTypeLabel,
  getRowTypeDotClass,
  getAnomalyTagColor,
  getAnomalyCategoryColor,
  getAnomalyTagClass,
  countAttentionRecords,
  recordMatchesStatFilter,
  needsAttentionRecord,
  clearRowCache: clearRowDisplayCache,
  invalidateRowCache: invalidateRowDisplayCache,
} = useTaskEditRecordDisplay(records, getDuplicateMeta, {
  isAbsentRow,
  hasManualCalibration,
  taskCountry: () => resolveTaskNightShiftCountryCode(
    task.value,
    countryStore.workingCountry,
    records.value,
    isConfirmedTask.value,
  ),
})

/** 与行展示缓存同生命周期：编辑单行时勿清空全表工时计算结果 */
const workHoursMemo = new Map()
const clearRowCache = () => {
  clearRowDisplayCache()
  workHoursMemo.clear()
}
const invalidateRowCache = (record) => {
  invalidateRowDisplayCache(record)
  const rowKey = record?._rowKey
  if (rowKey) workHoursMemo.delete(rowKey)
}

const exceptionTypeDeps = {
  hasRequiredMissing,
  hasFormatInvalid: (record) => {
    if (!record) return false
    const keys = ['Date', 'HORAIRES_DU_TRAVAIL', 'ARRIVEE', 'DEPAR']
    return keys.some((field) => isFormatFieldInvalid(record, field))
  },
  isAbsentRow,
  hasRecognitionNotes: (record) => hasAnomalyColumnContent(record),
}

const exceptionTypeSelectOptions = computed(() =>
  EXCEPTION_TYPE_VALUES.map((value) => ({
    value,
    label: t(EXCEPTION_TYPE_SHORT_I18N_KEYS[value]),
    title: t(EXCEPTION_TYPE_I18N_KEYS[value]),
  })),
)

/** 已选任意异常类型时只显示当前项；点击已选项再展开以便改选 */
const exceptionTypePickerOpen = ref({})

const isExceptionTypePickerCollapsed = (record) => {
  const type = normalizeExceptionType(record?.ExceptionType)
  if (!type) return false
  return !exceptionTypePickerOpen.value[getRowKey(record)]
}

const visibleExceptionTypeOptions = (record) => {
  const type = normalizeExceptionType(record?.ExceptionType)
  if (!isExceptionTypePickerCollapsed(record) || !type) return exceptionTypeSelectOptions.value
  return exceptionTypeSelectOptions.value.filter((opt) => opt.value === type)
}

const syncRecordExceptionType = (record) => {
  if (!record) return
  const prev = normalizeExceptionType(record.ExceptionType)
  ensureExceptionType(record, exceptionTypeDeps)
  const next = normalizeExceptionType(record.ExceptionType)
  if (prev === next) return
  // 类型被清空/变更时强制行刷新，避免仍锁字段
  const idx = records.value.findIndex((r) => r && r._rowKey === record._rowKey)
  if (idx >= 0) {
    records.value[idx] = { ...records.value[idx] }
  }
  invalidateRowCache(record)
}

const handleExceptionTypeSelect = (record, value) => {
  if (isExceptionTypeSelectDisabled(record, exceptionTypeDeps)) return
  const rowKey = getRowKey(record)
  const current = normalizeExceptionType(record.ExceptionType)
  const expanded = !!exceptionTypePickerOpen.value[rowKey]
  if (current && !expanded) {
    exceptionTypePickerOpen.value = { ...exceptionTypePickerOpen.value, [rowKey]: true }
    return
  }
  if (value === current && expanded) {
    const next = { ...exceptionTypePickerOpen.value }
    delete next[rowKey]
    exceptionTypePickerOpen.value = next
    return
  }
  onExceptionTypeChange(record, value, SNAPSHOT_FIELD_KEYS)
  const next = { ...exceptionTypePickerOpen.value }
  delete next[rowKey]
  exceptionTypePickerOpen.value = next
  invalidateRowCache(record)
  const idx = records.value.findIndex((r) => r && r._rowKey === record._rowKey)
  if (idx >= 0) {
    records.value[idx] = { ...records.value[idx] }
  }
  scheduleAnomalyCountUpdate(true)
  scheduleDraftSave(true)
}

/** 识别错误 / 纸质错误均框出日期·班次·到离·休息 */
const shouldHighlightFieldForCalibration = (record, field) => {
  const type = normalizeExceptionType(record?.ExceptionType)
  if (type !== EXCEPTION_TYPE.PAPER_OK_OCR_WRONG && type !== EXCEPTION_TYPE.PAPER_WRONG_TIME) {
    return false
  }
  const keys = Array.isArray(CALIBRATION_HIGHLIGHT_FIELDS) && CALIBRATION_HIGHLIGHT_FIELDS.length
    ? CALIBRATION_HIGHLIGHT_FIELDS
    : ['Date', 'HORAIRES_DU_TRAVAIL', 'ARRIVEE', 'DEPAR', 'PAUSE']
  return keys.includes(field)
}

const exceptionTypeDisabledHint = (record) => {
  if (!isExceptionTypeSelectDisabled(record, exceptionTypeDeps)) return ''
  if (hasRequiredMissing(record)) {
    const text = t('taskEdit.exceptionTypeDisabledMissing')
    return text !== 'taskEdit.exceptionTypeDisabledMissing'
      ? text
      : '请先补全必填信息后再选择异常类型'
  }
  if (exceptionTypeDeps.hasFormatInvalid(record)) {
    const text = t('taskEdit.exceptionTypeDisabledFormat')
    return text !== 'taskEdit.exceptionTypeDisabledFormat'
      ? text
      : '请先修正格式错误后再选择异常类型'
  }
  return ''
}

const exceptionTypeDisplayLabel = (record) => {
  if (isExceptionTypeExempt(record, isAbsentRow)) return '-'
  const type = normalizeExceptionType(record?.ExceptionType)
  if (!type) return '-'
  const shortKey = EXCEPTION_TYPE_SHORT_I18N_KEYS[type]
  return shortKey ? t(shortKey) : type
}

const fieldInputClass = (record, field) => {
  // 已删除 / 未出勤：不提示缺填或格式说明
  if (isRowMuted(record)) {
    return { 'row-muted-strike-text': true }
  }
  const requiredEmpty = isRequiredFieldEmpty(record, field)
  const formatInvalid = requiredInputClass(record, field)['format-invalid']
  const unreadable = isFieldUnreadable(record, field) && !requiredEmpty && !formatInvalid
  const classes = {
    ...requiredInputClass(record, field),
    'field-unreadable-cell': unreadable,
  }
  if (shouldHighlightFieldForCalibration(record, field)) {
    classes['exception-calibrate-required'] = true
  }
  return classes
}

const fieldTextClass = (record, field) => {
  if (isRowMuted(record)) {
    return { 'cell-text': true, 'row-muted-strike-text': true }
  }
  const requiredEmpty = isRequiredFieldEmpty(record, field)
  const formatInvalid = requiredTextClass(record, field)['format-invalid-display']
  let classes
  if (requiredEmpty) {
    classes = requiredTextClass(record, field)
  } else if (formatInvalid) {
    classes = requiredTextClass(record, field)
  } else if (isFieldUnreadable(record, field)) {
    classes = { 'cell-text': true, 'field-unreadable-cell': true }
  } else {
    classes = { 'cell-text': true }
  }
  if (shouldHighlightFieldForCalibration(record, field)) {
    classes = { ...classes, 'exception-calibrate-required-display': true }
  }
  return classes
}

const TIME_DISPLAY_FIELDS = ['HORAIRES_DU_TRAVAIL', 'ARRIVEE', 'DEPAR']

const displayRecordField = (record, field) => {
  if (isRequiredFieldEmpty(record, field)) {
    return displayFieldValue(record[field])
  }
  if (TIME_DISPLAY_FIELDS.includes(field) && isNonTimeFieldLabel(record[field])) {
    return t('taskEdit.fieldUnreadableShort')
  }
  if (isFieldUnreadable(record, field) && !sanitizeFieldValue(record[field])) {
    return t('taskEdit.fieldUnreadableShort')
  }
  return displayFieldValue(record[field])
}

const displayPaysField = (record) => {
  const base = displayRecordField(record, 'Pays')
  if (!base || base === t('taskEdit.fieldUnreadableShort')) return base
  return formatPaysFieldDisplay(record.Pays, countryStore.options)
}

const paysCountrySelectOptions = computed(() =>
  (countryStore.selectOptions || []).filter((item) => item.value && item.value !== 'default')
)

const paysFieldLockedHint = computed(() => t('taskEdit.paysLockedHint'))

const resolveRecordPaysSelectCode = (record) => {
  if (isConfirmedTask.value) {
    const fromPays = resolveCountryCodeFromPays(record?.Pays)
    if (fromPays && fromPays !== 'default') return fromPays
    const historical = taskWorkRegionCode.value
    return historical && historical !== 'default' ? historical : undefined
  }
  return resolveRecordPaysSelectCodeCore(record, taskWorkRegionCode.value, false) || undefined
}

const syncRecordPaysToTaskRegion = (record) => {
  if (!record || !isTaskCountryFollowingWorking.value || isRecordDeleted(record) || isAbsentRow(record)) return record
  const code = taskWorkRegionCode.value
  if (!code) return record
  const label = defaultPaysLabel(code)
  if (label) record.Pays = label
  return record
}

const fieldUnreadablePlaceholder = (record, field) => (
  isFieldUnreadable(record, field) && !isRequiredFieldEmpty(record, field)
    ? t('taskEdit.fieldUnreadableShort')
    : undefined
)

const fieldCellPlaceholder = (record, field) => {
  const unreadable = fieldUnreadablePlaceholder(record, field)
  if (unreadable) return unreadable
  if ((field === 'ARRIVEE' || field === 'DEPAR') && isArrivalDepartureSameTime(record)) {
    return t('fieldFormat.sameTimeShort')
  }
  if (isFormatFieldInvalid(record, field)) {
    const keys = getFormatHintKeys(field, { record, isSameArrivalDeparture: isArrivalDepartureSameTime })
    if (keys) return t(keys.short)
  }
  return undefined
}

const sameTimeHintText = (record) => {
  const text = t('fieldFormat.sameTimeTooltip')
  return text !== 'fieldFormat.sameTimeTooltip' ? text : '到达时间与离开时间不能相同，请核对后修改'
}

const fieldFormatTooltip = (record, field) => {
  if (isRowMuted(record)) return ''
  if ((field === 'ARRIVEE' || field === 'DEPAR') && isArrivalDepartureSameTime(record)) {
    return sameTimeHintText(record)
  }
  const keys = getFormatHintKeys(field, { record, isSameArrivalDeparture: isArrivalDepartureSameTime })
  return keys ? t(keys.tooltip) : ''
}

const showSameTimeHint = (record, field) => (
  !isRowMuted(record)
  && (field === 'ARRIVEE' || field === 'DEPAR')
  && isArrivalDepartureSameTime(record)
)

const showFormatHintBelow = (record, field) => (
  !isRowMuted(record) && isFormatFieldInvalid(record, field)
)

const fieldCellDomId = (record, field) => {
  if (!isFormatHintField(field)) return undefined
  return `field-cell-${getRowKey(record)}-${field}`
}

const customTableRow = (record) => ({
  id: `field-row-${getRowKey(record)}`,
})

const onTimeFieldInput = (record, field) => {
  invalidateRowCache(record)
  syncRecordExceptionType(record)
  if (field === 'ARRIVEE' || field === 'DEPAR') {
    scheduleAnomalyCountUpdate(true)
    scheduleRequiredMissingCountUpdate(true)
  }
}

const onClockTimeUpdate = (record, field, value) => {
  if (!record) return
  record[field] = value == null ? '' : value
  onTimeFieldInput(record, field)
}

const restoreFieldOriginal = (record, field) => {
  if (!record || !field || !record._aiBaseline || typeof record._aiBaseline !== 'object') return
  if (!Object.prototype.hasOwnProperty.call(record._aiBaseline, field)) return
  const original = record._aiBaseline[field]
  record[field] = original === undefined || original === null ? '' : original
  if (field === 'ARRIVEE' || field === 'DEPAR' || field === 'HORAIRES_DU_TRAVAIL' || field === 'Date') {
    applyFieldNormalization(record, field, { coerceTime: true })
  }
  if (field === 'PAUSE') {
    normalizeRecordPauseOnBlur(record)
    return
  }
  onReadableFieldChange(record, field)
}

/** 编辑前拍 AI 基线，保证识别说明能显示「由什么 → 改成什么」 */
const onCalibratableFieldFocusHandler = (record) => {
  onCalibratableFieldFocus(record, SNAPSHOT_FIELD_KEYS)
}

const onFormatFieldBlur = (record, field) => {
  applyFieldNormalization(record, field, { coerceTime: true })
  clearFieldUnreadable(record, field)
  onCalibratableFieldChange(record, SNAPSHOT_FIELD_KEYS)
  syncRecordExceptionType(record)
  invalidateRowCache(record)
  scheduleRequiredMissingCountUpdate(true)
  if (field === 'ARRIVEE' || field === 'DEPAR' || field === 'HORAIRES_DU_TRAVAIL' || field === 'Date') {
    scheduleAnomalyCountUpdate(true)
  }
}

const onFormatFieldPressEnter = (record, field, event) => {
  applyFieldNormalization(record, field, { coerceTime: true })
  onFormatFieldBlur(record, field)
  const target = event && event.target
  if (target && typeof target.blur === 'function') target.blur()
}

const scrollToFirstValidationIssue = async (issue) => {
  if (!issue) return
  const record = records.value[issue.line - 1]
  if (!record) return
  const field = (issue.fields && issue.fields[0]) || null
  const rowKey = getRowKey(record)
  const tableIndex = tableRecords.value.findIndex((r) => getRowKey(r) === rowKey)
  if (tableIndex >= 0 && tableIndex >= visibleRowCount.value) {
    visibleRowCount.value = Math.min(tableIndex + TABLE_SCROLL_BATCH, tableRecords.value.length)
    await nextTick()
  }
  await nextTick()
  const cellId = field ? fieldCellDomId(record, field) : `field-row-${rowKey}`
  const el = document.getElementById(cellId)
  if (!el) return
  el.scrollIntoView({ behavior: 'smooth', block: 'center', inline: 'nearest' })
  el.classList.add('validation-cell-flash')
  window.setTimeout(() => el.classList.remove('validation-cell-flash'), 2200)
}

/** DatePicker 只接受合法 YYYY-MM-DD；识别乱码/歧义日期传 null，避免面板出现 Invalid Date / NaN */
const datePickerValue = (raw) => {
  const str = raw == null ? '' : String(raw).trim()
  return isValidCanonicalDate(str) ? str : null
}

const dateFieldPlaceholder = (record) => {
  const str = record?.Date == null ? '' : String(record.Date).trim()
  if (str && !isValidCanonicalDate(str)) return str
  return fieldCellPlaceholder(record, 'Date') || fieldFormatTooltip(record, 'Date')
}

const onDatePickerUpdate = (record, value) => {
  if (!record) return
  record.Date = value || ''
  onReadableFieldChange(record, 'Date')
}

const applyFieldNormalization = (record, field, options = {}) => {
  if (!record) return
  const coerceTime = options.coerceTime === true
  if (field === 'NO') record.NO = normalizeWorkerNo(record.NO)
  if (field === 'NOM_PRENOM') record.NOM_PRENOM = normalizePersonName(record.NOM_PRENOM)
  if (field === 'Entrepot') record.Entrepot = normalizeLabelText(record.Entrepot)
  if (field === 'AGENCE_INTERIMAIRE') record.AGENCE_INTERIMAIRE = normalizeLabelText(record.AGENCE_INTERIMAIRE)
  if (field === 'Date') record.Date = normalizeDate(record.Date)
  // 时间友好转换仅在失焦/回车时做，避免输入「1」时立刻变成 01:00
  if (coerceTime && (field === 'ARRIVEE' || field === 'DEPAR')) {
    const next = normalizeClockTime(record[field])
    if (next !== undefined && next !== null) record[field] = next
  }
  if (coerceTime && field === 'HORAIRES_DU_TRAVAIL') {
    const next = normalizeShiftSchedule(record[field])
    if (next !== undefined && next !== null) record[field] = next
  }
  if (TIME_DISPLAY_FIELDS.includes(field) && isNonTimeFieldLabel(record[field])) {
    record[field] = ''
    if (!Array.isArray(record._unreadableFields)) {
      record._unreadableFields = []
    }
    if (!record._unreadableFields.includes(field)) {
      record._unreadableFields.push(field)
    }
  }
}

const onReadableFieldChange = (record, field) => {
  applyFieldNormalization(record, field)
  clearFieldUnreadable(record, field)
  if (field === 'NOM_PRENOM') markNameManuallyEdited(record)
  if (field === 'ARRIVEE' || field === 'DEPAR' || field === 'HORAIRES_DU_TRAVAIL' || field === 'Date') {
    refreshRecordNightShiftMark(record)
  }
  onCalibratableFieldChange(record, SNAPSHOT_FIELD_KEYS)
  syncRecordExceptionType(record)
  invalidateRowCache(record)
  if (field === 'ARRIVEE' || field === 'DEPAR' || field === 'HORAIRES_DU_TRAVAIL' || field === 'PAUSE') {
    scheduleAnomalyCountUpdate(true)
  }
  scheduleDraftSave()
}

const getRowAnomalyReasons = (record) => getRecordAnomalyReasons(record)

const rawData = ref('')
const activeStatFilter = ref('')

const onStatFilterSelect = (key) => {
  activeStatFilter.value = activeStatFilter.value === key ? '' : (key || '')
  resetVisibleTableRows()
}
const VALIDATION_BANNER_DEBOUNCE_MS = 450
const {
  previewImagesList,
  previewCurrentIndex,
  previewDockOpen,
  previewFullscreenOpen,
  openPreviewFullscreen,
  loadTaskImageUrls,
  openImagePreviewAt,
} = useTaskImagePreview()
const task = ref(null)
const countryStore = useCountryStore()
const canDeleteTask = computed(() => {
  if (!task.value) return false
  if (task.value.status === 'confirmed') {
    return authStore.canDeleteConfirmedTask
  }
  return true
})
const deleteModalOpen = ref(false)
const deleteRedirectTo = ref('/tasks')

const taskWorkRegionDisplayCode = computed(() =>
  resolveTaskWorkRegionDisplayCountryCode(
    task.value,
    countryStore.workingCountry,
    records.value,
    isConfirmedTask.value,
  )
)

const taskWorkRegionSourceCode = computed(() =>
  isConfirmedTask.value
    ? resolveTaskWorkRegionHistoricalCountryCode(task.value, records.value)
    : resolveTaskWorkRegionCode(task.value, countryStore.workingCountry)
)

const taskWorkRegionBannerCode = computed(() =>
  resolveTaskWorkRegionBannerCode(
    task.value,
    countryStore.workingCountry,
    records.value,
    isConfirmedTask.value,
  )
)

const taskWorkRegionLabel = computed(() =>
  resolveTaskWorkRegionLabel(
    task.value,
    countryStore.options,
    countryStore.workingCountry,
    records.value,
    isConfirmedTask.value,
  )
)

const taskWorkRegionCode = computed(() =>
  resolveTaskWorkRegionBindingCode(
    task.value,
    countryStore.workingCountry,
    records.value,
    isConfirmedTask.value,
  ) || '',
)

const hasTaskWorkRegion = computed(() => Boolean(taskWorkRegionCode.value || taskWorkRegionBannerCode.value))

const resolvePaysRegionCode = (pays) => {
  const fromPays = resolveCountryCodeFromPays(pays)
  if (fromPays && fromPays !== 'default') return fromPays
  const trimmed = String(pays || '').trim()
  if (!trimmed) return ''
  return trimmed.toUpperCase()
}

const isPaysRegionMismatch = (pays, taskRegion) => {
  if (!taskRegion) return false
  const paysCode = resolvePaysRegionCode(pays)
  return Boolean(paysCode && paysCode !== taskRegion)
}

const workRegionMismatchCount = computed(() => {
  if (isConfirmedTask.value) return 0
  const taskRegion = taskWorkRegionCode.value
  if (!taskRegion || !records.value?.length) return 0
  return records.value.filter((record) => {
    if (record.deleted || record.isDeleted) return false
    return isPaysRegionMismatch(record.Pays, taskRegion)
  }).length
})

const workRegionMismatch = computed(() => workRegionMismatchCount.value > 0)

const isConfirmedTask = computed(() => task.value?.status === 'confirmed')
const isTaskCountryFollowingWorking = computed(() => checkTaskCountryFollowingWorking(task.value))
const canShowSubmitBar = computed(() => task.value?.status === 'processed')
const submitValidationCount = ref(0)
let requiredValidationDebounceTimer = null
const scheduleRequiredMissingCountUpdate = (immediate = false) => {
  if (requiredValidationDebounceTimer) {
    window.clearTimeout(requiredValidationDebounceTimer)
    requiredValidationDebounceTimer = null
  }
  if (immediate) {
    submitValidationCount.value = countSubmitValidationLines(records.value)
    return
  }
  requiredValidationDebounceTimer = window.setTimeout(() => {
    requiredValidationDebounceTimer = null
    submitValidationCount.value = countSubmitValidationLines(records.value)
  }, VALIDATION_BANNER_DEBOUNCE_MS)
}

const attentionAlertCount = ref(0)
let anomalyCountDebounceTimer = null
const scheduleAnomalyCountUpdate = (immediate = false) => {
  if (anomalyCountDebounceTimer) {
    window.clearTimeout(anomalyCountDebounceTimer)
    anomalyCountDebounceTimer = null
  }
  if (immediate) {
    attentionAlertCount.value = countAttentionRecords(records.value)
    return
  }
  anomalyCountDebounceTimer = window.setTimeout(() => {
    anomalyCountDebounceTimer = null
    attentionAlertCount.value = countAttentionRecords(records.value)
  }, VALIDATION_BANNER_DEBOUNCE_MS)
}

const reviewIssuesChipVisible = computed(() => {
  const blocked = !isConfirmedTask.value && submitValidationCount.value > 0
  return blocked || attentionAlertCount.value > 0
})

const submitBlockedLineSet = computed(() => {
  if (isConfirmedTask.value) return new Set()
  const issues = collectSubmitValidationIssues(records.value, collectConfirmValidationIssues)
  return new Set(issues.map((issue) => issue.line))
})

const showRequiredValidationDetail = async () => {
  const issues = collectSubmitValidationIssues(records.value, collectConfirmValidationIssues)
  if (issues.length > 0) {
    await scrollToFirstValidationIssue(issues[0])
    showConfirmValidationModal(issues)
  }
}
const canCalibrateRecord = computed(
  () => authStore.userInfo?.permissions?.recordCalibrate === true
)
const isRecordEditable = (record) =>
  !isConfirmedTask.value && !isRecordDeleted(record) && !isAbsentRow(record)

/** 姓名 / 仓库 / 中介：不因「考勤正确」锁死，行可编辑时随时可改 */
const EXCEPTION_TYPE_UNLOCKED_FIELDS = new Set([
  'NOM_PRENOM',
  'Entrepot',
  'AGENCE_INTERIMAIRE',
])

const isFieldEditable = (record, field) => {
  if (!isRecordEditable(record)) return false
  if (EXCEPTION_TYPE_UNLOCKED_FIELDS.has(field)) return true
  // 格式不正确时始终允许改该字段，避免被「考勤正确」锁死
  if (isFormatFieldInvalid(record, field)) return true
  if (isConfiguredRequiredField(field) && !canEditRequiredFields(record)) return false
  return true
}

const calibrationVisible = ref(false)
const calibrationRecord = ref(null)
const calibrationSubmitting = ref(false)
const activeHeaderFilterField = ref('')
const headerFilterDraft = ref('')
const headerFilters = ref({})

const syncAlertType = computed(() => {
  const s = task.value?.syncStatus
  if (s === 'synced') return 'success'
  if (s === 'sync_failed') return 'error'
  return 'info'
})

const syncAlertMessage = computed(() => {
  const s = task.value?.syncStatus
  if (s === 'pending') return t('taskEdit.syncPending')
  if (s === 'synced') return t('taskEdit.syncSynced')
  if (s === 'sync_failed') return t('taskEdit.syncFailed')
  return ''
})

const canRetrySync = computed(() =>
  task.value?.status === 'confirmed' && task.value?.syncStatus === 'sync_failed'
)

const clearSyncPoll = () => {
  if (stopSyncPoll) {
    stopSyncPoll()
    stopSyncPoll = null
  }
}

const startSyncPoll = () => {
  clearSyncPoll()
  stopSyncPoll = startAdaptivePoll(
    () => task.value?.syncStatus === 'pending',
    () => refreshSyncStatusOnly(),
    { intervalMs: 4000, maxIntervalMs: 12000 },
  )
}

const refreshSyncStatusOnly = async () => {
  try {
    const res = await getTaskProgress(taskId.value)
    const progress = res.data
    if (!progress || !task.value) return
    const prevSync = task.value.syncStatus
    task.value = {
      ...task.value,
      status: progress.status ?? task.value.status,
      syncStatus: progress.syncStatus ?? task.value.syncStatus,
      syncError: progress.syncError ?? task.value.syncError,
    }
    if (prevSync === 'pending' && progress.syncStatus !== 'pending') {
      clearSyncPoll()
      await loadTask(true)
    }
  } catch (e) {
    console.error('刷新飞书同步状态失败:', e)
  }
}

const ROW_MUTED_EXEMPT_KEYS = new Set(['action'])
const ROW_MUTED_TEXT = '#73707F'
const ROW_MUTED_STRIKE_DELETED = '#D94040'
const ROW_MUTED_STRIKE_ABSENT = '#B8860B'

const isRecordDeleted = (record) => Boolean(record?.isDeleted || record?.deleted)
const isRowMuted = (record) => isRecordDeleted(record) || isAbsentRow(record)
const resolveRowMutedStrikeColor = (record) => (
  isRecordDeleted(record) ? ROW_MUTED_STRIKE_DELETED : ROW_MUTED_STRIKE_ABSENT
)

const getRowClassName = (record) => {
  const base = getBaseRowClassName(record) || ''
  // 未出勤 / 已删除：仅删除线，不加底纹，绝不叠待确认底
  if (
    isRowMuted(record)
    || base.includes('deleted-row')
    || base.includes('absent-row')
  ) {
    return base
  }
  if (
    !isConfirmedTask.value
    && isExceptionTypeMissingForSubmit(record, exceptionTypeDeps)
  ) {
    return [base, 'exception-type-pending-row'].filter(Boolean).join(' ')
  }
  return base
}

const rowMutedStrikeClass = (record) => (isRowMuted(record) ? 'row-muted-strike-text' : '')

const mutedStrikeStyle = (record) => {
  if (!isRowMuted(record)) return undefined
  return {
    textDecoration: 'line-through',
    textDecorationColor: resolveRowMutedStrikeColor(record),
    textDecorationThickness: '2px',
    fontStyle: 'italic',
    color: ROW_MUTED_TEXT,
  }
}

const mergeCellProps = (props, columnKey) => {
  const wrapKeys = ['anomalyReasons']
  const centerKeys = new Set(['ExceptionType', 'action'])
  const classes = [
    props.class,
    wrapKeys.includes(columnKey) ? 'cell-wrap' : '',
    centerKeys.has(columnKey) ? 'cell-v-middle cell-h-center' : 'cell-v-middle cell-h-left',
  ].filter(Boolean)
  if (!classes.length) return props
  return { ...props, class: classes.join(' ') }
}

const cellStyle = (record, rowIndex, columnKey) => {
  if (!record) return {}
  if (isRowMuted(record)) {
    const exempt = ROW_MUTED_EXEMPT_KEYS.has(columnKey)
    // 未出勤/删除：白底无底纹，仅删除线 + 弱化字色
    const style = {
      backgroundColor: '#FFFFFF',
      color: ROW_MUTED_TEXT,
      fontStyle: 'italic',
    }
    if (!exempt) {
      style.textDecoration = 'line-through'
      style.textDecorationColor = resolveRowMutedStrikeColor(record)
      style.textDecorationThickness = '2px'
    }
    return mergeCellProps({
      style,
      class: exempt ? 'row-muted-exempt-cell' : 'row-muted-strike-cell',
    }, columnKey)
  }
  const fieldKey = columnKey
  const calibrate = fieldKey && shouldHighlightFieldForCalibration(record, fieldKey)
  if ((fieldKey === 'ARRIVEE' || fieldKey === 'DEPAR') && isArrivalDepartureSameTime(record)) {
    return mergeCellProps({
      class: calibrate
        ? 'format-time-cell format-time-cell--invalid exception-calibrate-required-cell'
        : 'format-time-cell format-time-cell--invalid',
    }, columnKey)
  }
  if (fieldKey && isConfiguredRequiredField(fieldKey) && isRequiredFieldEmpty(record, fieldKey)) {
    return mergeCellProps({
      class: calibrate ? 'required-field-cell exception-calibrate-required-cell' : 'required-field-cell',
    }, columnKey)
  }
  if (calibrate) {
    return mergeCellProps({ class: 'exception-calibrate-required-cell' }, columnKey)
  }
  if (
    columnKey === 'ExceptionType'
    && !isConfirmedTask.value
    && !isRowMuted(record)
    && isExceptionTypeMissingForSubmit(record, exceptionTypeDeps)
  ) {
    return mergeCellProps({ class: 'exception-type-pending-cell' }, columnKey)
  }
  if (fieldKey === 'Pays' && taskWorkRegionCode.value && isPaysRegionMismatch(record.Pays, taskWorkRegionCode.value)) {
    return mergeCellProps({ class: 'work-region-mismatch-cell' }, columnKey)
  }
  if ((record?.SmartMark || '').includes('模糊')) {
    return mergeCellProps({ style: { backgroundColor: '#FFF9EC' } }, columnKey)
  }
  return mergeCellProps({}, columnKey)
}

const baseColumns = computed(() => {
  void locale.value
  return buildRecognitionTableColumns(t, {
    requiredFieldKeys: confirmRequiredFields.value,
    cellStyle,
    includeWorkHours: true,
    searchFields: true,
    fixedAction: true,
    actionColumnWidth: 40,
    useExceptionTypeColumn: true,
    exceptionTypeColumnWidth: 96,
    compactIdentityColumns: true,
    includeSerialNoColumn: false,
    fixedAnomalyReasons: true,
    anomalyReasonsColumnWidth: 220,
  })
})
const { columns: sortedColumns, onSorterToggle, sortRows } = useTableColumnSort(baseColumns, { customHeader: true })

const filteredRecords = computed(() => {
  let list = records.value
  const statKey = activeStatFilter.value
  if (statKey === 'submitBlocked') {
    const lines = submitBlockedLineSet.value
    list = list.filter((_, idx) => lines.has(idx + 1))
  } else if (statKey === 'anomaly' || statKey === 'attention') {
    list = list.filter((row) => needsAttentionRecord(row))
  } else if (statKey) {
    list = list.filter((row) => recordMatchesStatFilter(row, statKey))
  }
  const active = Object.entries(headerFilters.value).filter(([, v]) => {
    if (Array.isArray(v)) return v.length > 0
    if (v && typeof v === 'object') return Boolean(v.from?.trim() || v.to?.trim())
    return String(v || '').trim() !== ''
  })
  if (!active.length) return list
  return list.filter((row) => active.every(([field, value]) => {
    const column = sortedColumns.value.find((c) => c.searchField === field)
    const filterType = resolveColumnFilterType(column || { searchField: field })
    const keyword = serializeFilterValue(filterType, value)
    return matchRecordByFilter(filterType, field, keyword, row)
  }))
})

const tableRecords = computed(() => sortRows(filteredRecords.value))

const visibleTableRecords = computed(() => tableRecords.value.slice(0, visibleRowCount.value))

const hasMoreTableRows = computed(() => visibleRowCount.value < tableRecords.value.length)

const resetVisibleTableRows = () => {
  visibleRowCount.value = TABLE_SCROLL_BATCH
}

const loadMoreTableRows = () => {
  if (!hasMoreTableRows.value || loadingMoreTableRows.value) return
  loadingMoreTableRows.value = true
  window.requestAnimationFrame(() => {
    visibleRowCount.value = Math.min(
      visibleRowCount.value + TABLE_SCROLL_BATCH,
      tableRecords.value.length,
    )
    loadingMoreTableRows.value = false
    nextTick(() => bindTableLoadMoreObserver())
  })
}

const bindTableLoadMoreObserver = () => {
  if (!tableLoadMoreObserver) return
  tableLoadMoreObserver.disconnect()
  const el = tableLoadMoreSentinel.value
  if (el && hasMoreTableRows.value) {
    tableLoadMoreObserver.observe(el)
  }
}

const recordLineNo = (record) => {
  if (!record || !record._rowKey) return ''
  const idx = records.value.findIndex((r) => r && r._rowKey === record._rowKey)
  return idx >= 0 ? idx + 1 : ''
}

const resetTableColumnsLock = () => {
  columnsLocked.value = false
  lockedSizedColumns.value = []
}

const { columns: sizedColumns } = useAutoSizedColumns(sortedColumns, tableRecords, {
  enabled: computed(() => !columnsLocked.value),
  actionWidth: isConfirmedTask.value && canCalibrateRecord.value ? 72 : 40,
  defaultMin: 56,
  defaultMax: 160,
  bodyFont: '12px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
  font: '600 11px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
  getCellSample: (col, record) => {
    if (col.key === 'workHours') return calculateWorkHours(record)
    if (col.key === 'anomalyReasons') {
      return getRecordAnomalyReasons(record).join('; ')
    }
    if (col.key === 'PAUSE') return formatPauseDisplay(record.PAUSE)
    if (col.key === 'PAGE_NUM') return displayFieldValue(record.PAGE_NUM || record.pageNum)
    if (col.key === 'EMPLOYEE_NO') return displayRecordField(record, 'EMPLOYEE_NO')
    return undefined
  },
})

const effectiveSizedColumns = computed(() => (
  columnsLocked.value && lockedSizedColumns.value.length
    ? lockedSizedColumns.value
    : sizedColumns.value
))

const {
  columns: resizedColumns,
  isColumnResizable,
  startColumnResize,
} = useTableColumnResize('task-edit-v4', effectiveSizedColumns, {
  nonResizableKeys: ['action', 'ExceptionType', 'PAGE_NUM', 'NO', 'EMPLOYEE_NO'],
  minWidth: 28,
})

watch(
  () => sizedColumns.value,
  (cols) => {
    if (columnsLocked.value || !cols?.length || !records.value.length) return
    lockedSizedColumns.value = cols.map((col) => ({ ...col }))
    columnsLocked.value = true
  },
  { immediate: true },
)

watch(locale, () => {
  clearRowCache()
  const titleByKey = new Map((baseColumns.value || []).map((col) => [col.key, col.title]))
  if (!titleByKey.size || !lockedSizedColumns.value.length) return
  lockedSizedColumns.value = lockedSizedColumns.value.map((col) => (
    titleByKey.has(col.key) ? { ...col, title: titleByKey.get(col.key) } : col
  ))
})

const {
  frozenColumns: frozenDisplayColumns,
  hiddenKeys,
  frozenKeys,
  configurableColumns,
  setHiddenKeys,
  setFrozenKeys,
  showAllColumns,
  clearFrozenKeys,
} = useColumnFreeze('task-edit-v4', resizedColumns, {
  defaultFrozen: ['PAGE_NUM', 'NO'],
  defaultHidden: ['EMPLOYEE_NO'],
  preserveRightFixed: true,
})

const MICRO_ID_WIDTH = {
  PAGE_NUM: 40,
  NO: 32,
  EMPLOYEE_NO: 56,
}

const columns = computed(() =>
  (frozenDisplayColumns.value || []).map((col) => {
    const forced = MICRO_ID_WIDTH[col.key]
    if (!forced) return col
    return {
      ...col,
      width: forced,
      minWidth: forced,
      maxWidth: forced,
      autoWidth: false,
    }
  }),
)
const scrollX = computed(() => sumTableScrollX(columns.value))

const isHeaderFilterActive = (field) => {
  const value = headerFilters.value[field]
  const column = columns.value.find((c) => c.searchField === field)
  const filterType = resolveColumnFilterType(column || { searchField: field })
  return isFilterActive(filterType, value)
}

const handleHeaderPopoverOpen = (column, open) => {
  const field = column?.searchField
  if (!field) return
  const filterType = resolveColumnFilterType(column)
  if (open) {
    activeHeaderFilterField.value = field
    const stored = headerFilters.value[field]
    if (stored !== undefined && stored !== null && isFilterActive(filterType, stored)) {
      headerFilterDraft.value = Array.isArray(stored)
        ? [...stored]
        : (typeof stored === 'object' ? { ...stored } : stored)
    } else {
      headerFilterDraft.value = emptyFilterValue(filterType)
    }
  } else if (activeHeaderFilterField.value === field) {
    activeHeaderFilterField.value = ''
  }
}

const applyHeaderFilter = () => {
  if (!activeHeaderFilterField.value) return
  const column = columns.value.find((c) => c.searchField === activeHeaderFilterField.value)
  const filterType = resolveColumnFilterType(column || { searchField: activeHeaderFilterField.value })
  headerFilters.value = {
    ...headerFilters.value,
    [activeHeaderFilterField.value]: isFilterActive(filterType, headerFilterDraft.value)
      ? headerFilterDraft.value
      : emptyFilterValue(filterType),
  }
  activeHeaderFilterField.value = ''
}

const clearHeaderFilter = () => {
  if (!activeHeaderFilterField.value) return
  const column = columns.value.find((c) => c.searchField === activeHeaderFilterField.value)
  const filterType = resolveColumnFilterType(column || { searchField: activeHeaderFilterField.value })
  headerFilters.value = {
    ...headerFilters.value,
    [activeHeaderFilterField.value]: emptyFilterValue(filterType),
  }
  headerFilterDraft.value = emptyFilterValue(filterType)
  activeHeaderFilterField.value = ''
}

const isAuxHeaderColumn = (column) => {
  const key = String(column?.key || '')
  return key.includes('EXPAND_COLUMN') || key.includes('SELECTION_COLUMN')
}

const NARROW_HEADER_COLUMN_KEYS = new Set([
  'NO', 'PAGE_NUM', 'Pays', 'PAUSE', 'EMPLOYEE_NO', 'ARRIVEE', 'DEPAR', 'workHours', 'SIGNATURE',
  'Observations', 'Date', 'HORAIRES_DU_TRAVAIL',
])

const MICRO_HEADER_COLUMN_KEYS = new Set(['PAGE_NUM', 'NO', 'EMPLOYEE_NO'])

const isNarrowHeaderColumn = (column) => NARROW_HEADER_COLUMN_KEYS.has(column?.key)

const isMicroHeaderColumn = (column) => MICRO_HEADER_COLUMN_KEYS.has(column?.key)

const resolveHeaderHint = (column) => {
  if (!column) return ''
  if (column.key === 'Pays' && hasTaskWorkRegion.value) {
    return t('taskEdit.workRegionPaysColumnHint', { region: taskWorkRegionLabel.value })
  }
  if (column.key === 'ExceptionType') {
    const text = t('taskEdit.exceptionTypeColumnHint')
    return text && text !== 'taskEdit.exceptionTypeColumnHint' ? text : ''
  }
  if (column.formatHintTooltipKey) {
    const text = t(column.formatHintTooltipKey)
    return text && text !== column.formatHintTooltipKey ? text : ''
  }
  return ''
}

const formatHeaderTitle = (title) => {
  if (Array.isArray(title)) {
    return title.join('').trim()
  }
  return title == null ? '' : String(title)
}

const getCalibrationHistoryReversed = (record) =>
  [...parseCalibrationHistory(record)].reverse()

const formatCalibrationTime = (at) => {
  if (!at) return '—'
  const s = String(at).replace('T', ' ')
  return s.length > 19 ? s.slice(0, 19) : s
}

const formatCalibrationHistoryChanges = (entry) =>
  formatHistoryChanges(entry, (field) => t(FIELD_LABEL_KEYS[field] || field))

const openCalibration = (record) => {
  if (!canCalibrateRecord.value) {
    message.warning(t('calibration.permissionDenied'))
    return
  }
  calibrationRecord.value = { ...record }
  calibrationVisible.value = true
}

const applyCalibratedRecord = (updated) => {
  if (!updated?._rowKey) return
  const idx = records.value.findIndex((r) => r._rowKey === updated._rowKey)
  if (idx >= 0) {
    records.value[idx] = normalizeRecordPause({
      ...records.value[idx],
      ...updated,
      _rowKey: updated._rowKey,
    })
  }
}

const handleCalibrationSubmit = async ({ rowKey, updates, reason }) => {
  calibrationSubmitting.value = true
  try {
    const res = await calibrateTaskRecord(taskId.value, { rowKey, updates, reason })
    message.success(t('calibration.syncingFeishu'))
    calibrationVisible.value = false
    if (res.data?.record) {
      const updated = res.data.record
      if (updated.SmartMark) {
        updated.Mark = updated.SmartMark
      }
      applyCalibratedRecord(updated)
    } else {
      await loadTask(true)
    }
    if (res.data?.syncStatus) {
      task.value = {
        ...task.value,
        syncStatus: res.data.syncStatus,
        syncError: res.data.syncError ?? task.value?.syncError,
      }
    }
    if (task.value?.syncStatus === 'pending') {
      startSyncPoll()
    }
  } catch (e) {
    console.error(e)
  } finally {
    calibrationSubmitting.value = false
  }
}

const loadTask = async (silent = false, options = {}) => {
  const { reloadRecords = true } = options
  if (!silent) loading.value = true
  draftSaveBlocked = true
  try {
    const response = await getTaskDetail(taskId.value)
    task.value = response.data
    const taskCountry = resolveTaskNightShiftCountryCode(
      task.value,
      countryStore.workingCountry,
      records.value,
      isConfirmedTask.value,
    ) || getCachedWorkingCountry()

    if (task.value?.syncStatus === 'pending') {
      startSyncPoll()
    } else {
      clearSyncPoll()
    }

    if (!reloadRecords) {
      return
    }

    await loadNightShiftRules(true, taskCountry)
    resetVisibleTableRows()
    resetTableColumnsLock()
    clearRowCache()
    activeStatFilter.value = ''
    
    const dataPayload = resolveTaskRecordsJson(task.value)
    if (dataPayload) {
      const parsedRecords = JSON.parse(dataPayload)
      records.value = parsedRecords.map((record, idx) => {
        const normalized = prepareRecordPlaceholders(normalizeRecordPause({
          ...record,
          isDeleted: Boolean(record.isDeleted || record.deleted),
          _rowKey: record._rowKey || `${taskId.value}-${idx}`,
          _baseName: stripSerialSuffix(String(sanitizeFieldValue(record.NOM_PRENOM) || '').trim()),
          _nameAutoNumbered: false,
          _duplicateConfirmedUnique: false
        }))
        const signatureMark = computeSignatureMark(normalized)
        normalized.SIGNATURE = signatureMark
        normalized.CHECKER = signatureMark
        refreshRecordNightShiftMark(normalized)
        syncRecordExceptionType(normalized)
        ensureAiBaseline(normalized, SNAPSHOT_FIELD_KEYS)
        return normalized
      })
      await fetchConfirmedDuplicateHints()
      scheduleRequiredMissingCountUpdate(true)
      scheduleAnomalyCountUpdate(true)
    }
    rawData.value = task.value.aiRawOutput || ''
    
    await loadTaskImageUrls(task.value)
  } catch (error) {
    const msg = String(error?.message || '')
    if (msg.includes(t('errors.taskNotFound')) || /任务不存在|任务已删除|Task not found/i.test(msg)) {
      message.warning(t('notification.taskDeleted'))
      router.replace('/tasks')
      return
    }
    message.error(t('taskEdit.loadingFailed'))
    console.error(error)
  } finally {
    draftSaveBlocked = false
    if (!silent) loading.value = false
  }
}

const handleRetrySync = async () => {
  if (!canRetrySync.value) return
  retryingSync.value = true
  try {
    await retryFeishuSync(taskId.value)
    message.success(t('taskEdit.syncRetrySuccess'))
    await loadTask(true)
  } catch (error) {
    message.error(t('taskEdit.syncRetryFailed'))
    console.error(error)
  } finally {
    retryingSync.value = false
  }
}

const getExportFilename = (response, fallback) => {
  const disposition = response.headers.get('Content-Disposition') || response.headers.get('content-disposition')
  const match = disposition && disposition.match(/filename\*?=(?:UTF-8''|")?([^";]+)/i)
  if (!match) return fallback
  try {
    return decodeURIComponent(match[1].replace(/"/g, ''))
  } catch (e) {
    return match[1].replace(/"/g, '')
  }
}

const handleExportExcel = async () => {
  if (!taskId.value) return
  try {
    const token = getToken()
    const locale = encodeURIComponent(currentExportLocale())
    const exportRecords = records.value.map((record) => {
      const normalized = stripRecordMetadata(sanitizeRecordPlaceholders(normalizeRecordPause({ ...record })))
      // 保留 AI 基线供「修改前后」sheet；去掉仅前端用的行键
      delete normalized._rowKey
      return normalized
    })
    const response = await fetch(`${API_BASE_PATH}/local/export/${taskId.value}/xlsx?locale=${locale}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify({ records: exportRecords }),
    })
    const contentType = response.headers.get('content-type') || ''
    if (!response.ok || contentType.includes('application/json')) {
      let reason = `${response.status} ${response.statusText}`
      if (contentType.includes('application/json')) {
        try {
          const payload = await response.json()
          reason = translateApiError(payload)
        } catch {
          // ignore parse failure
        }
      }
      message.error(reason || t('taskEdit.exportFailed'))
      return
    }
    const blob = await response.blob()
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = getExportFilename(response, `attendance_${taskId.value}.xlsx`)
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
  } catch (error) {
    message.error(t('taskEdit.exportFailed'))
    console.error('Export Excel failed:', error)
  }
}

const handleDeleteSubmit = async (reason) => {
  deleting.value = true
  try {
    const needsReason = task.value?.status === 'confirmed'
    await deleteTask(taskId.value, needsReason ? reason : undefined)
    message.success(t('tasks.deleteSuccess'))
    deleteModalOpen.value = false
    router.push(deleteRedirectTo.value)
  } catch (error) {
    message.error(t('taskEdit.deleteFailed'))
    console.error(error)
  } finally {
    deleting.value = false
  }
}

const handleDeleteTask = () => {
  if (!canDeleteTask.value) {
    message.warning(t('taskEdit.deleteNotAllowed'))
    return
  }
  deleteRedirectTo.value = '/tasks'
  deleteModalOpen.value = true
}

const handleReupload = () => {
  if (!canDeleteTask.value) {
    message.warning(t('taskEdit.deleteNotAllowed'))
    return
  }
  deleteRedirectTo.value = '/home'
  deleteModalOpen.value = true
}

const openImagePreview = (index) => {
  openImagePreviewAt(index)
}

const getFileName = (url) => {
  const name = fileNameFromImageUrl(url)
  return name || t('taskEdit.unknownFile')
}

const handleAddManualRecord = () => {
  if (!canShowSubmitBar.value) return
  const draft = createManualTaskRecord({
    taskId: taskId.value,
    taskCountry: resolveManualRecordCountryCode(task.value, countryStore.workingCountry)
      || getCachedWorkingCountry(),
    existingRecords: records.value,
  })
  const normalized = prepareRecordPlaceholders(normalizeRecordPause(draft))
  const signatureMark = computeSignatureMark(normalized)
  normalized.SIGNATURE = signatureMark
  normalized.CHECKER = signatureMark
  syncRecordExceptionType(normalized)
  ensureAiBaseline(normalized, SNAPSHOT_FIELD_KEYS)
  records.value = [...records.value, normalized]
  invalidateRowCache(normalized)
  scheduleDuplicateRecheck(0)
  scheduleRequiredMissingCountUpdate(true)
  scheduleAnomalyCountUpdate(true)
  scheduleDraftSave(true)
  if (visibleRowCount.value < records.value.length) {
    visibleRowCount.value = records.value.length
  }
  message.success(t('taskEdit.manualRowAdded'))
  nextTick(() => {
    tableLoadMoreSentinel.value?.scrollIntoView?.({ behavior: 'smooth', block: 'nearest' })
  })
}

const toggleDelete = (record, index) => {
  if (isAbsentRow(record) && !record.isDeleted) {
    record._prevMark = record.SmartMark
    record.SmartMark = '正常'
    record._restored = true
    records.value.splice(index, 1, record)
    invalidateRowCache(record)
    scheduleDraftSave(true)
    return
  }
  record.isDeleted = !record.isDeleted
  if (record.isDeleted) {
    record._prevMark = record.SmartMark
    record.SmartMark = '已删除'
  } else {
    if (record._prevMark && record._prevMark !== '未出勤') {
      record.SmartMark = record._prevMark
      delete record._prevMark
    } else {
      record.SmartMark = '正常'
    }
  }
  syncRecordExceptionType(record)
  records.value.splice(index, 1, record)
  invalidateRowCache(record)
  scheduleDuplicateRecheck(0)
  scheduleDraftSave(true)
}

const calculateWorkHours = (record) => {
  const rowKey = record?._rowKey
  const fp = `${record?.isDeleted ? 1 : 0}|${record?.ARRIVEE || ''}|${record?.DEPAR || ''}|${record?.PAUSE ?? ''}|${isAbsentRow(record) ? 1 : 0}`
  if (rowKey) {
    const hit = workHoursMemo.get(rowKey)
    if (hit && hit.fp === fp) return hit.value
  }

  let value = '-'
  if (!(record?.isDeleted || isAbsentRow(record))) {
    const arriveTime = record?.ARRIVEE
    const departTime = record?.DEPAR
    const pauseMinutes = normalizePauseMinutes(record?.PAUSE)

    if (arriveTime && departTime && arriveTime !== '???' && departTime !== '???') {
      const arriveMinutes = parseTimeToMinutes(arriveTime)
      const departMinutes = parseTimeToMinutes(departTime)
      if (arriveMinutes !== null && departMinutes !== null) {
        let totalMinutes = departMinutes - arriveMinutes
        if (totalMinutes < 0) totalMinutes += 24 * 60
        const pause = (pauseMinutes !== null && pauseMinutes !== undefined && pauseMinutes !== '') ? Number(pauseMinutes) : 0
        const workMinutes = totalMinutes - pause
        if (workMinutes >= 0) value = (workMinutes / 60).toFixed(2)
      }
    }
  }

  if (rowKey) {
    workHoursMemo.set(rowKey, { fp, value })
    if (workHoursMemo.size > 800) {
      workHoursMemo.delete(workHoursMemo.keys().next().value)
    }
  }
  return value
}

const parseTimeToMinutes = (timeStr) => {
  if (!timeStr || timeStr.trim() === '' || timeStr === '???') {
    return null
  }
  
  const cleanTime = timeStr.trim().replace(',', '.').replace('h', ':').replace('H', ':')
  const parts = cleanTime.split(':')
  
  if (parts.length === 2) {
    const hours = parseInt(parts[0], 10)
    const minutes = parseInt(parts[1], 10)
    if (!isNaN(hours) && !isNaN(minutes)) {
      return hours * 60 + minutes
    }
  } else if (parts.length === 1) {
    const num = parseFloat(parts[0])
    if (!isNaN(num)) {
      return Math.floor(num) * 60 + Math.round((num % 1) * 60)
    }
  }
  
  return null
}

const getRowKey = (record) => record?._rowKey || `${record?.NO || 'row'}-${record?.Date || ''}-${record?.NOM_PRENOM || ''}`

const normalizePauseMinutes = (value) => {
  if (value === null || value === undefined || value === '') return ''
  const normalized = String(value)
    .trim()
    .toLowerCase()
    .replace(',', '.')
    .replace(/\s+/g, '')
    .replace(/minutes?|mins?|mn/g, 'min')
  if (isPlaceholderValue(normalized)) return ''

  const hourMatch = normalized.match(/^(\d+(?:\.\d+)?)h(\d+(?:\.\d+)?)?(?:min|m)?$/)
  if (hourMatch) {
    const hours = Number(hourMatch[1])
    const minutes = hourMatch[2] ? Number(hourMatch[2]) : 0
    return Number.isNaN(hours) || Number.isNaN(minutes) ? value : Math.round(hours * 60 + minutes)
  }
  const colonMatch = normalized.match(/^(\d{1,2}):(\d{1,2})$/)
  if (colonMatch) return Number(colonMatch[1]) * 60 + Number(colonMatch[2])
  const minuteMatch = normalized.match(/^(\d+(?:\.\d+)?)(?:min|m)?$/)
  if (minuteMatch) {
    const minutes = Number(minuteMatch[1])
    return Number.isNaN(minutes) ? value : Math.round(minutes)
  }
  return value
}

const normalizeRecordPause = (record) => {
  const base = {
    ...record,
    PAUSE: normalizePauseMinutes(record?.PAUSE),
    PAGE_NUM: record?.PAGE_NUM ?? record?.pageNum ?? '',
    Entrepot: sanitizeEntrepot(record?.Entrepot),
  }
  if (!isTaskCountryFollowingWorking.value) {
    return base
  }
  return syncRecordPaysToTaskRegion(applyWorkingCountryPays(
    base,
    taskWorkRegionCode.value || getCachedWorkingCountry(),
  ))
}

const formatPauseDisplay = (value) => {
  const minutes = normalizePauseMinutes(value)
  return minutes === '' ? '-' : `${minutes} min`
}

const formatDuplicateCell = (value, type = 'text') => {
  if (type === 'pause') {
    return formatPauseDisplay(value)
  }
  return displayFieldValue(value)
}

const normalizeRecordPauseOnBlur = (record) => {
  if (!record) return
  const minutes = normalizePauseMinutes(record.PAUSE)
  record.PAUSE = minutes === '' ? null : Number(minutes)
  onCalibratableFieldChange(record, SNAPSHOT_FIELD_KEYS)
  syncRecordExceptionType(record)
  invalidateRowCache(record)
  scheduleAnomalyCountUpdate(true)
  scheduleDraftSave()
}

const refreshRecordNightShiftMark = (record) => {
  if (!record || record.isDeleted) return
  const regionCode = resolveTaskNightShiftCountryCode(
    task.value,
    countryStore.workingCountry,
    records.value,
    isConfirmedTask.value,
  ) || getCachedWorkingCountry()
  const refreshed = refreshNightShiftInSmartMark(
    getRawSmartMark(record),
    record,
    regionCode,
  )
  record.SmartMark = refreshed
  if (record.Mark != null && record.Mark !== '') {
    record.Mark = refreshed
  }
}

/** 确认前草稿：保留 ExceptionType / 手动标记 / AI 基线，刷新不丢 */
const DRAFT_SAVE_DEBOUNCE_MS = 700
let draftSaveTimer = null
let draftSavePromise = null
let draftSaveQueued = false
let draftSaveBlocked = false

const buildDraftPayload = () => records.value.map((record) => {
  const normalized = sanitizeRecordPlaceholders(normalizeRecordPause({ ...record }))
  refreshRecordNightShiftMark(normalized)
  return normalized
})

const flushDraftSave = async () => {
  if (draftSaveTimer) {
    window.clearTimeout(draftSaveTimer)
    draftSaveTimer = null
  }
  if (draftSaveBlocked || loading.value || isConfirmedTask.value) return
  if (!taskId.value || task.value?.status !== 'processed') return
  if (!records.value.length) return
  if (draftSavePromise) {
    draftSaveQueued = true
    return draftSavePromise
  }
  const data = buildDraftPayload()
  draftSavePromise = saveTaskDraft(taskId.value, { data })
    .catch((error) => {
      console.warn('save draft failed', error)
    })
    .finally(() => {
      draftSavePromise = null
      if (draftSaveQueued) {
        draftSaveQueued = false
        void flushDraftSave()
      }
    })
  return draftSavePromise
}

const scheduleDraftSave = (immediate = false) => {
  if (draftSaveBlocked || loading.value || isConfirmedTask.value) return
  if (!taskId.value || task.value?.status !== 'processed') return
  if (draftSaveTimer) window.clearTimeout(draftSaveTimer)
  if (immediate) {
    void flushDraftSave()
    return
  }
  draftSaveTimer = window.setTimeout(() => {
    draftSaveTimer = null
    void flushDraftSave()
  }, DRAFT_SAVE_DEBOUNCE_MS)
}

const handleSubmit = async () => {
  records.value.forEach((record) => syncRecordExceptionType(record))
  const preparedRecords = records.value.map((record) => {
    const normalized = stripRecordMetadata(sanitizeRecordPlaceholders(normalizeRecordPause(record)))
    refreshRecordNightShiftMark(normalized)
    return normalized
  })
  if (!validateBeforeConfirm(preparedRecords)) return

  const nonDeletedRecords = preparedRecords.filter((r) => !r.isDeleted && !isAbsentRow(r))

  if (nonDeletedRecords.length === 0) {
    message.warning(t('taskEdit.noValidRecords'))
    return
  }
  
  const anomalyRecords = records.value.filter(r => {
    if (r.isDeleted) return false
    return getRecordAnomalyReasons(r).length > 0
  })
  
  const allAnomalyReasons = []
  anomalyRecords.forEach(r => {
    const reasons = getRecordAnomalyReasons(r)
    reasons.forEach(reason => {
      if (!allAnomalyReasons.includes(reason)) {
        allAnomalyReasons.push(reason)
      }
    })
  })
  
  const anomalySummary = JSON.stringify({
    totalRecords: records.value.length,
    validRecords: nonDeletedRecords.length,
    deletedRecords: records.value.filter(r => r.isDeleted).length,
    anomalyRecords: anomalyRecords.length,
    anomalyReasons: allAnomalyReasons,
    riskLevel: anomalyRecords.length > 0 ? 'high' : 'none'
  })
  
  submitting.value = true
  draftSaveBlocked = true
  if (draftSaveTimer) {
    window.clearTimeout(draftSaveTimer)
    draftSaveTimer = null
  }
  try {
    await confirmTask(taskId.value, { 
      data: preparedRecords,
      anomalySummary: anomalySummary
    })
    message.success(t('taskEdit.submitSuccess'))
    await loadTask(true)
  } catch (error) {
    message.error(t('taskEdit.submitFailed'))
    console.error(error)
  } finally {
    draftSaveBlocked = false
    submitting.value = false
  }
}

let isComponentMounted = true

watch([tableLoadMoreSentinel, hasMoreTableRows], () => {
  nextTick(() => bindTableLoadMoreObserver())
})

onMounted(async () => {
  isComponentMounted = true
  if (typeof IntersectionObserver !== 'undefined') {
    tableLoadMoreObserver = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          loadMoreTableRows()
        }
      },
      { root: null, rootMargin: '160px', threshold: 0 },
    )
    nextTick(() => bindTableLoadMoreObserver())
  }
  if (!authStore.userInfo?.permissions) {
    try {
      await authStore.fetchUserInfo()
    } catch {
      /* ignore */
    }
  }
  try {
    const res = await fetchConfirmValidationConfig()
    applyConfirmValidationConfig(res.data)
  } catch (e) {
    console.error('加载确认校验配置失败:', e)
  }
  try {
    await countryStore.hydrate()
  } catch {
    /* ignore */
  }
  loadTask()
})

onUnmounted(() => {
  isComponentMounted = false
  expandedDuplicateRowKeys.value = []
  if (requiredValidationDebounceTimer) window.clearTimeout(requiredValidationDebounceTimer)
  if (anomalyCountDebounceTimer) window.clearTimeout(anomalyCountDebounceTimer)
  if (draftSaveTimer) window.clearTimeout(draftSaveTimer)
  void flushDraftSave()
  tableLoadMoreObserver?.disconnect()
  tableLoadMoreObserver = null
  clearSyncPoll()
})

watch(taskId, async (nextId, prevId) => {
  if (draftSaveTimer) {
    window.clearTimeout(draftSaveTimer)
    draftSaveTimer = null
  }
  const prevStatus = task.value?.status
  const snapshot = records.value
  if (
    prevId
    && prevId !== nextId
    && prevStatus === 'processed'
    && Array.isArray(snapshot)
    && snapshot.length > 0
  ) {
    try {
      await saveTaskDraft(prevId, {
        data: snapshot.map((record) => sanitizeRecordPlaceholders(normalizeRecordPause({ ...record }))),
      })
    } catch (error) {
      console.warn('flush draft on task switch failed', error)
    }
  }
  if (isComponentMounted) {
    activeStatFilter.value = ''
    resetVisibleTableRows()
    resetTableColumnsLock()
    clearRowCache()
    loadTask()
  }
})

watch(
  () => countryStore.workingCountry,
  () => {
    if (!isComponentMounted || !isTaskCountryFollowingWorking.value || !records.value.length) return
    records.value.forEach((record) => {
      syncRecordPaysToTaskRegion(record)
      refreshRecordNightShiftMark(record)
    })
    scheduleRequiredMissingCountUpdate(true)
    scheduleAnomalyCountUpdate(true)
  },
)

const DUPLICATE_WATCH_FIELDS = [
  'NOM_PRENOM', 'Pays', 'Entrepot', 'Date', 'AGENCE_INTERIMAIRE',
  'ARRIVEE', 'DEPAR', '_duplicateConfirmedUnique', 'isDeleted', 'NO', 'SmartMark', 'Mark',
]

const VALIDATION_WATCH_FIELDS = [
  'NO', 'NOM_PRENOM', 'Pays', 'Entrepot', 'Date', 'AGENCE_INTERIMAIRE',
  'HORAIRES_DU_TRAVAIL', 'ARRIVEE', 'DEPAR', 'PAUSE', 'SIGNATURE', 'Observations', 'PAGE_NUM',
  'SmartMark', 'Mark', 'ExceptionType', 'isDeleted', '_duplicateConfirmedUnique', '_unreadableFields',
]

function buildRecordsFieldSignature(list, fields) {
  if (!Array.isArray(list) || !list.length) return ''
  const parts = new Array(list.length)
  for (let i = 0; i < list.length; i++) {
    const record = list[i]
    const rowParts = new Array(fields.length)
    for (let j = 0; j < fields.length; j++) {
      const field = fields[j]
      const value = record[field]
      rowParts[j] = Array.isArray(value) ? value.join('|') : (value == null ? '' : String(value))
    }
    parts[i] = rowParts.join('\x01')
  }
  return parts.join('\x02')
}

const validationWatchSignature = computed(() =>
  buildRecordsFieldSignature(records.value, VALIDATION_WATCH_FIELDS))

const duplicateWatchSignature = computed(() =>
  buildRecordsFieldSignature(records.value, DUPLICATE_WATCH_FIELDS))

watch(validationWatchSignature, () => {
  scheduleRequiredMissingCountUpdate()
  scheduleAnomalyCountUpdate()
})

watch(duplicateWatchSignature, () => {
  if (duplicateRefreshing.value) return
  scheduleDuplicateRecheck(300)
})

watch(headerFilters, () => {
  resetVisibleTableRows()
}, { deep: true })
</script>

<style lang="scss" scoped>
.task-edit-container {
  padding: 0;

  &.has-sticky-submit {
    padding-bottom: calc(80px + env(safe-area-inset-bottom, 0px));
  }

  .image-compare-layout {
    gap: $space-3;
    min-height: 0;
  }

  :deep(.page-shell) {
    margin-bottom: 8px;
    gap: 8px 12px;
  }

  :deep(.page-shell__title) {
    font-size: $font-size-xl;
    line-height: 1.2;
  }

  :deep(.page-shell__meta) {
    margin-top: 2px;
  }

  .edit-card {
    border-radius: $radius-lg;
    box-shadow: $shadow-xs;

    :deep(.ant-card-body) {
      padding: 10px 14px 12px;
    }
  }

  :deep(.stat-overview.single-row) {
    margin-bottom: 8px;
  }

  .record-count {
    font-size: 13px;
    color: $primary;
    background: $primary-light;
    padding: 4px 12px;
    border-radius: $radius-xl;
    border: 1px solid $border-accent;
  }

  .task-header-meta {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 6px;
    font-size: $font-size-sm;
    color: $text-secondary;
    line-height: 1.5;

    &--warning {
      .task-header-meta__value {
        color: $warning-dark;
      }
    }

    &__label {
      font-weight: $font-weight-semibold;
      color: $text-tertiary;
    }

    &__value {
      font-weight: $font-weight-semibold;
      color: $text-primary;
    }

    &__dot {
      color: $text-tertiary;
    }

    &__hint {
      color: $text-secondary;
    }

    &__info {
      color: $text-tertiary;
      cursor: help;
      font-size: 13px;

      &:hover {
        color: $primary;
      }
    }

    &__badge {
      margin: 0;
    }
  }

  .page-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 24px;
    padding-bottom: 16px;
    border-bottom: 1px solid $border;

    .header-left {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .header-right {
      .record-count {
        font-size: 13px;
        color: $text-tertiary;
        background: $primary-light;
        color: $primary;
        padding: 4px 12px;
        border-radius: $radius-xl;
        border: 1px solid $border-accent;
      }
    }

    .page-title {
      margin: 0;
      font-size: $font-size-2xl;
      font-weight: $font-weight-extrabold;
      color: $text-strong;
      letter-spacing: -0.02em;
    }

    .task-id-tag {
      font-size: 13px;
    }
    
    .status-tag {
      font-size: 13px;
    }
    
    .header-right {
      display: flex;
      align-items: center;
      gap: 16px;
      
      .reupload-btn {
        height: 36px;
        border-radius: $radius-sm;
        font-weight: $font-weight-medium;
      }
    }
  }

  .sync-alert {
    margin-bottom: 16px;
  }

  .sync-alert-message {
    display: inline-flex;
    align-items: center;
    gap: 8px;
  }

  .sync-alert-spin {
    font-size: 14px;
  }

  .edit-panel {
    margin-top: 4px;
  }

  .edit-toolbar {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 6px 10px;
    margin-bottom: 8px;
    min-height: 28px;

    &__title {
      font-size: $font-size-base;
      font-weight: $font-weight-semibold;
      color: $text-strong;
      white-space: nowrap;
    }

    &__add-row {
      flex-shrink: 0;
    }

    &__scope {
      flex-shrink: 0;
    }

    &__spacer {
      flex: 1 1 auto;
      min-width: 8px;
    }

    &__issues {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      flex: none;
      height: 32px;
      padding: 0;
      border: 0;
      background: transparent;
      color: $text-primary;
    }

    &__issues-lead {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      font-size: 13px;
      font-weight: $font-weight-medium;
      color: $text-secondary;
      white-space: nowrap;
      flex-shrink: 0;
      pointer-events: none;
    }

    &__issues-icon {
      color: $text-secondary;
      font-size: 14px;
      flex-shrink: 0;
    }

    &__issues-group {
      display: inline-flex;
      align-items: stretch;
      height: 32px;
      overflow: hidden;
      border: 1px solid #eaecf0;
      border-radius: 6px;
      background: linear-gradient(180deg, #fafbfc 0%, #f2f4f7 100%);
    }

    &__issues-filter {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      margin: 0;
      height: 100%;
      padding: 0 12px;
      border: 0;
      border-radius: 0;
      background: transparent;
      color: $text-primary;
      font-size: 13px;
      font-weight: $font-weight-medium;
      line-height: 1.2;
      white-space: nowrap;
      cursor: pointer;
      text-decoration: none;

      &:hover:not(.is-active) {
        color: $text-strong;
        background: rgba(255, 255, 255, 0.7);
      }

      &.is-active {
        color: $text-strong;
        background: #fff;
        font-weight: $font-weight-semibold;
        text-decoration: none;
      }
    }

    &__issues-group :deep(> span) {
      display: inline-flex;
      min-width: 0;
      height: 100%;
    }

    &__issues-group :deep(> span + span) .edit-toolbar__issues-filter {
      box-shadow: inset 1px 0 0 #eaecf0;
    }

    &__issues-clear {
      font-size: 11px;
      opacity: 0.7;
      flex-shrink: 0;
    }
  }

  .cell-text {
    font-size: 12px;
    color: $text-primary;
  }

  .mark-tag {
    font-size: $font-size-sm;
    border-radius: 4px;
  }

  .anomaly-hint {
    margin-bottom: 10px;
    padding: 8px 12px;
    background: $warning-light;
    border-radius: 10px;
    border-left: 4px solid $warning;
    overflow: visible;

    .anomaly-summary {
      display: flex;
      align-items: center;
      gap: 10px;
    }

    .anomaly-icon {
      color: $warning-dark;
      font-size: 18px;
    }

    .anomaly-text {
      font-size: $font-size-md;
      color: $accent-dark;
      font-weight: $font-weight-semibold;
    }

    .anomaly-toggle {
      font-size: 13px;
      color: $primary;
      padding: 0;
      height: auto;
    }

    .anomaly-detail-list {
      margin-top: 14px;
      padding: 10px 8px 4px;
      border-top: 1px solid rgba(60, 60, 67, 0.12);
      max-height: min(42vh, 360px);
      overflow-y: auto;
      overflow-x: hidden;
      background: rgba(255, 251, 230, 0.9);
      border-radius: 0 0 8px 8px;
      min-height: 48px;

      .anomaly-more-hint {
        padding-top: 8px;
        font-size: 12px;
        color: $text-secondary;
      }

      .anomaly-item {
        display: flex;
        align-items: flex-start;
        gap: 12px;
        padding: 8px 4px;
        font-size: 13px;
        border-bottom: 1px solid rgba(60, 60, 67, 0.06);

        &:last-child {
          border-bottom: none;
        }

        .anomaly-index {
          display: inline-flex;
          align-items: center;
          justify-content: center;
          width: 22px;
          height: 22px;
          border-radius: 50%;
          background: rgba($primary, 0.12);
          color: $primary;
          font-size: $font-size-sm;
          font-weight: $font-weight-bold;
          flex-shrink: 0;
        }

        .anomaly-name {
          color: $text-primary;
          font-weight: $font-weight-semibold;
          min-width: 120px;
          flex-shrink: 0;
        }

        .anomaly-reasons {
          display: flex;
          flex-wrap: wrap;
          gap: 8px;
          flex: 1;
          min-width: 0;
        }
      }
    }
  }

  .duplicate-scope-label {
    font-size: $font-size-sm;
    color: $text-secondary;
    font-weight: $font-weight-semibold;
    white-space: nowrap;
  }

  :deep(.work-region-mismatch-cell) {
    background: rgba($warning-color, 0.1) !important;
  }

  :deep(.pays-country-select) {
    width: 100%;
    min-width: 0;
    font-size: 12px;

    .ant-select-selector {
      font-size: 12px !important;
      padding-inline: 2px !important;
    }

    .ant-select-selection-item,
    .ant-select-selection-placeholder {
      font-size: 12px !important;
      line-height: 22px !important;
    }

    &.ant-select-disabled .ant-select-selector {
      color: inherit;
      background: transparent;
      cursor: default;
    }
  }

  :deep(.signature-mark-select) {
    width: 100%;
    font-size: 12px;

    .ant-select-selector {
      font-size: 12px !important;
      padding-inline: 2px !important;
    }

    .ant-select-selection-item,
    .ant-select-selection-placeholder {
      font-size: 12px !important;
      line-height: 22px !important;
    }
  }

  :deep(.signature-mark-tag) {
    font-size: 12px;
  }

  .table-scroll-load-more {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    min-height: 44px;
    margin: 8px 0 4px;
    color: $text-tertiary;
    font-size: $font-size-sm;
  }

  .table-scroll-load-more__text {
    color: $text-secondary;
  }

  .edit-table {
    .name-cell {
      min-width: 0;
    }

    .name-cell :deep(.ant-input) {
      white-space: normal;
    }

    .name-cell .cell-text {
      white-space: normal;
      word-break: break-word;
      line-height: 1.35;
    }

    .name-cell {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    .mark-tags-cell {
      display: flex;
      flex-wrap: wrap;
      gap: 4px;
      align-items: center;
    }

    .calibration-tag {
      cursor: pointer;
    }

    .duplicate-tools {
      display: flex;
      align-items: center;
      gap: 6px;
      flex-wrap: wrap;
    }

    .duplicate-link {
      padding: 0;
      height: auto;
      font-size: $font-size-sm;
    }

    .inline-anomaly-tags {
      display: flex;
      flex-wrap: wrap;
      gap: 4px;
      align-items: center;

      :deep(.ant-tag) {
        margin-right: 0;
        line-height: 20px;
      }
    }

    .recognition-note-list {
      display: flex;
      flex-direction: column;
      justify-content: center;
      align-items: flex-start;
      gap: 2px;
      width: 100%;
      min-width: 0;
      text-align: left;
      line-height: 1.3;
      font-size: 11px;
    }

    .recognition-note-list__item {
      display: flex;
      align-items: flex-start;
      gap: 4px;
      width: 100%;
      min-width: 0;
    }

    .recognition-note-list__index {
      flex: none;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 12px;
      height: 12px;
      margin-top: 2px;
      border-radius: 999px;
      font-size: 9px;
      font-weight: 600;
      font-variant-numeric: tabular-nums;
      line-height: 1;
      color: #fff;
      background: $text-tertiary;
      box-shadow: 0 0 0 1px rgba(28, 26, 46, 0.04);
    }

    .recognition-note-list__text {
      min-width: 0;
      flex: 1;
      padding-top: 0;
      font-weight: 400;
      letter-spacing: 0;
      white-space: normal;
      word-break: break-word;
      color: $text-primary;
    }

    .recognition-note-list__text--link {
      margin: 0;
      padding: 0;
      border: 0;
      background: transparent;
      cursor: pointer;
      text-align: left;
      font: inherit;
      font-weight: 400;
      color: inherit;

      &:hover {
        text-decoration: underline;
      }
    }

    .recognition-note-list__item--night {
      .recognition-note-list__index {
        background: linear-gradient(135deg, #7C5CFF 0%, #9B7BFF 100%);
      }
      .recognition-note-list__text {
        color: #6B4FE0;
      }
    }

    .recognition-note-list__item--shift {
      .recognition-note-list__index {
        background: $accent-gradient;
      }
      .recognition-note-list__text {
        color: $accent-dark;
      }
    }

    .recognition-note-list__item--danger {
      .recognition-note-list__index {
        background: linear-gradient(135deg, $danger 0%, lighten($danger, 6%) 100%);
      }
      .recognition-note-list__text {
        color: $danger-dark;
      }
    }

    .recognition-note-list__item--warning {
      .recognition-note-list__index {
        background: linear-gradient(135deg, #E8A317 0%, $warning 100%);
      }
      .recognition-note-list__text {
        color: $warning-dark;
      }
    }

    .recognition-note-list__item--accent {
      .recognition-note-list__index {
        background: $accent-gradient;
      }
      .recognition-note-list__text {
        color: $accent-dark;
      }
    }

    .recognition-note-list__item--primary {
      .recognition-note-list__index {
        background: $primary-gradient;
      }
      .recognition-note-list__text {
        color: $primary-dark;
      }
    }

    .recognition-note-list__item--success {
      .recognition-note-list__index {
        background: linear-gradient(135deg, $success-dark 0%, $success 100%);
      }
      .recognition-note-list__text {
        color: $success-dark;
      }
    }

    .recognition-note-list__item--default {
      .recognition-note-list__index {
        background: linear-gradient(135deg, #8A8796 0%, $text-secondary 100%);
      }
      .recognition-note-list__text {
        color: $text-strong;
      }
    }

    :deep(th.col-micro-id),
    :deep(td.col-micro-id) {
      padding-left: 2px !important;
      padding-right: 2px !important;
    }

    :deep(th.col-micro-id) {
      .table-sortable-header {
        justify-content: center;
      }

      .table-sortable-header__title {
        justify-content: center;
        flex: 0 1 auto;
      }
    }

    :deep(td.cell-v-middle) {
      vertical-align: middle !important;
    }

    :deep(td.cell-h-center) {
      text-align: center !important;
    }

    :deep(td.cell-h-left) {
      text-align: left !important;

      .ant-input,
      .ant-input-number-input,
      .ant-picker-input > input,
      .ant-select-selection-item,
      .cell-text,
      .work-hours,
      .recognition-note-list {
        text-align: left;
      }

      .ant-input-number {
        width: 100%;
      }

      .recognition-note-list {
        align-items: flex-start;
      }

      .recognition-note-list__item {
        justify-content: flex-start;
      }
    }

    :deep(td.cell-h-center .inline-anomaly-tags) {
      justify-content: center;
    }

    :deep(td.cell-h-center .exception-type-cell),
    :deep(td.cell-h-center .table-action-cell) {
      margin-inline: auto;
    }

    .exception-type-cell {
      display: flex;
      flex-direction: column;
      justify-content: center;
      align-items: stretch;
      min-height: 100%;
      width: 100%;
    }

    .table-action-cell {
      justify-content: center;
      width: auto;
      max-width: 100%;
      margin-inline: auto;
    }

    .table-action-cell--icons-1 {
      width: 32px;
    }

    .table-action-cell--icons-mixed {
      width: auto;
      min-width: 32px;
      max-width: 72px;
    }

    .format-field-cell {
      :deep(.ant-input),
      :deep(.ant-picker),
      :deep(.ant-input-number) {
        padding-left: 2px !important;
        padding-right: 2px !important;
      }

      :deep(.clock-time-field .ant-input-affix-wrapper) {
        padding-left: 2px !important;
        padding-right: 3px !important;
      }

      :deep(.clock-time-field .ant-input-affix-wrapper > input.ant-input) {
        padding-right: 0 !important;
      }
    }

    .cell-muted {
      color: $text-tertiary;
    }

    :deep(.ant-table) {
      border-radius: 10px;
      border: 1px solid $border;
    }

    :deep(.ant-table-thead > tr > th) {
      overflow: visible;
      padding: 4px 4px !important;
      font-size: 11px;
      line-height: 1.2;
      font-weight: 600;
    }

    :deep(.ant-table-expand-icon-col),
    :deep(th.ant-table-row-expand-icon-cell),
    :deep(td.ant-table-row-expand-icon-cell) {
      display: none !important;
      width: 0 !important;
      min-width: 0 !important;
      max-width: 0 !important;
      padding: 0 !important;
      border: none !important;
    }

    :deep(.ant-table-container) {
      overflow-x: auto;
    }

    :deep(.ant-table-expanded-row > td) {
      overflow: visible;
      padding: 8px 12px !important;
      background: transparent;
    }

    :deep(.ant-table-tbody > tr > td) {
      border-bottom: 1px solid $border;
      padding: 3px 4px !important;
      vertical-align: top;
      line-height: 1.25;
      font-size: 12px;
    }

    :deep(.ant-table-tbody > tr:hover > td) {
      background: $bg-muted !important;
    }

    /* 未出勤/删除：强制白底，压过通用 hover 的 $bg-muted */
    :deep(.ant-table-tbody > tr.deleted-row > td),
    :deep(.ant-table-tbody > tr.absent-row > td),
    :deep(.ant-table-tbody > tr.deleted-row:hover > td),
    :deep(.ant-table-tbody > tr.absent-row:hover > td) {
      background: #FFFFFF !important;
      background-color: #FFFFFF !important;
    }

    :deep(.ant-input) {
      font-size: 12px;
      padding: 1px 4px;
      line-height: 1.3;
      border-radius: $radius-sm;
      background: transparent;
      transition: all $duration-base $ease-smooth;

      &:focus, &:hover {
        background: $bg-surface;
        box-shadow: 0 0 0 2px rgba($primary, 0.15);
      }
    }

    :deep(.ant-input-number) {
      font-size: 12px;

      .ant-input-number-input {
        padding: 1px 4px;
        height: 22px;
      }
    }

    :deep(.ant-input-number-focused) {
      box-shadow: 0 0 0 2px rgba($primary, 0.15);
    }
  }

  .row-type-dot {
    display: inline-block;
    width: 8px;
    height: 8px;
    border-radius: 50%;
    margin-right: 6px;
    vertical-align: middle;

    &.dot-normal { background-color: $success; }
    &.dot-blurred { background-color: $warning; }
    &.dot-handwritten { background-color: $primary; }
    &.dot-absent { background-color: $text-tertiary; }
    &.dot-deleted { background-color: $text-tertiary; }
  }

  .row-type-label {
    font-size: $font-size-sm;
    color: $text-secondary;
    vertical-align: middle;
    font-weight: $font-weight-medium;
  }

  .action-btn {
    padding: 4px 8px;
  }

  $required-empty-surface: rgba($danger, 0.11);
  $required-empty-border: rgba($danger, 0.42);
  $required-empty-text: $danger-dark;

  :deep(.required-field-cell) {
    background: $required-empty-surface !important;
    box-shadow: inset 0 0 0 1px $required-empty-border;
  }

  :deep(th.col-required-header) {
    .ant-table-column-title {
      color: $text-primary;
      font-weight: 600;
    }
  }

  .required-validation-banner {
    margin-bottom: 8px;

    .required-validation-detail-btn {
      padding: 0 0 0 8px;
      height: auto;
      line-height: inherit;
    }
  }

  :deep(.required-empty) {
    background: rgba($danger, 0.09) !important;
    border: 1px solid $required-empty-border !important;
    border-radius: $radius-sm;
    box-shadow: none;
    color: $required-empty-text;

    &:hover,
    &:focus,
    &.ant-input-number-focused {
      background: $required-empty-surface !important;
      border-color: rgba($danger, 0.55) !important;
      box-shadow: 0 0 0 2px $danger-ring;
    }

    input {
      border-color: transparent !important;
      color: $required-empty-text;
    }
  }

  /* 需校准字段：去掉左侧色条；红框 + 明显浅红填充，表示可改 */
  :deep(.exception-calibrate-required-cell) {
    background: transparent !important;
    box-shadow: none;
  }

  :deep(.exception-calibrate-required) {
    border: 1px solid $danger !important;
    border-radius: $radius-sm;
    background: rgba($danger, 0.16) !important;
    box-shadow: none !important;

    &:hover,
    &:focus,
    &.ant-input-number-focused,
    &.ant-picker-focused {
      border-color: $danger-dark !important;
      box-shadow: 0 0 0 2px $danger-ring !important;
      background: rgba($danger, 0.22) !important;
    }

    .ant-picker-input > input,
    input {
      background: transparent !important;
    }
  }

  .exception-calibrate-required-display {
    border: 1px solid $danger;
    border-radius: $radius-sm;
    padding: 1px 4px;
    background: rgba($danger, 0.16);
    box-shadow: none;
  }

  :deep(.exception-type-pending-cell) {
    background: transparent !important;
    box-shadow: none;
    vertical-align: middle !important;
  }

  /*
   * 待确认：纸面轻洗（$accent-light）+ 左侧珊瑚条。
   * 不铺饱和橙；行动点交给异常类型分段控件。未出勤/删除不加此类。
   */
  :deep(.exception-type-pending-row > td) {
    background: $accent-light !important;
  }

  :deep(.exception-type-pending-row > td:first-child) {
    box-shadow: inset 3px 0 0 $accent;
  }

  :deep(.exception-type-pending-row:hover > td) {
    background: mix($accent-light, $accent, 8%) !important;
  }

  :deep(.exception-type-pending-row.deleted-row > td),
  :deep(.exception-type-pending-row.absent-row > td),
  :deep(.exception-type-pending-row.deleted-row:hover > td),
  :deep(.exception-type-pending-row.absent-row:hover > td) {
    background: #FFFFFF !important;
  }

  :deep(.exception-type-pending-row.deleted-row > td:first-child),
  :deep(.exception-type-pending-row.absent-row > td:first-child) {
    box-shadow: none;
  }

  :deep(.exception-type-pending-row > td.exception-type-pending-cell) {
    background: transparent !important;
  }

  :deep(.exception-type-pending-row .recognition-note-list__text) {
    padding: 0;
    border-radius: 0;
    background: transparent;
    box-shadow: none;
  }

  :deep(.exception-type-pending-row .delete-btn.ant-btn-dangerous),
  :deep(.exception-type-pending-row .delete-btn) {
    color: rgba($danger-dark, 0.78) !important;

    &:hover,
    &:focus {
      color: $danger-dark !important;
      background: rgba($danger, 0.1) !important;
    }
  }

  .exception-type-cell {
    min-width: 0;
    display: flex;
    flex-direction: column;
    justify-content: center;
    width: 100%;
  }

  .exception-type-stack {
    display: flex;
    flex-direction: column;
    gap: 2px;
    width: 100%;
    min-width: 0;

    &.is-collapsed .exception-type-stack__opt.is-active {
      cursor: pointer;
    }

    /* 外层禁用态、以及每个选项上的 a-tooltip 包一层，需撑满窄列 */
    :deep(> .ant-tooltip-disabled-compatible-wrapper),
    :deep(> span) {
      display: block !important;
      width: 100%;
    }
  }

  .exception-type-stack__opt {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 3px;
    width: 100%;
    margin: 0;
    padding: 3px 4px;
    border: 1px solid rgba($border, 0.85);
    border-radius: 4px;
    background: $bg-surface;
    color: $text-tertiary;
    font-size: 11px;
    font-weight: $font-weight-medium;
    line-height: 1.25;
    text-align: center;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    cursor: pointer;
    transition: background $duration-fast $ease-smooth,
      border-color $duration-fast $ease-smooth,
      color $duration-fast $ease-smooth;

    .exception-type-stack__check {
      flex: none;
      font-size: 10px;
      font-weight: $font-weight-bold;
      line-height: 1;
    }

    .exception-type-stack__label {
      min-width: 0;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    &:hover:not(:disabled):not(.is-active) {
      border-color: rgba($text-secondary, 0.4);
      color: $text-secondary;
      background: $bg-muted;
    }

    &:focus-visible {
      outline: 2px solid rgba($primary, 0.3);
      outline-offset: 1px;
    }

    /* 选中：深绿 / 深橙 / 深红底 + 白字 */
    &.is-active {
      font-weight: $font-weight-semibold;
      border-color: transparent;
      color: #fff;
    }

    &.is-active.exception-type-stack__opt--attendance_ok {
      background: $success-dark;
    }

    &.is-active.exception-type-stack__opt--paper_ok_ocr_wrong {
      background: $warning-dark;
    }

    &.is-active.exception-type-stack__opt--paper_wrong_time {
      background: $danger-dark;
    }

    &:disabled,
    &.is-disabled {
      cursor: not-allowed;
      opacity: 0.42;
    }
  }

  /*
   * 待选：三颗独立幽灵按钮（圆角、浅填、珊瑚描边），
   * 能看出可点，又贴合 $accent-light 行底。写在 opt 默认样式之后。
   */
  .exception-type-stack.is-pending {
    gap: 3px;
    padding: 0;
    border: 0;
    background: transparent;

    .exception-type-stack__opt:not(.is-active) {
      border: 1px solid rgba($accent-dark, 0.28);
      border-radius: 5px;
      background: rgba(255, 255, 255, 0.55);
      color: $accent-dark;
      font-weight: $font-weight-semibold;
      box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.7);

      &:hover:not(:disabled) {
        background: rgba(255, 255, 255, 0.88);
        border-color: $accent-dark;
        color: darken($accent-dark, 6%);
        box-shadow: 0 1px 2px rgba($accent-dark, 0.12);
      }
    }
  }

  .exception-type-readonly {
    font-size: 12px;
  }

  .required-empty-display {
    color: $required-empty-text;
    font-weight: 600;
    background: $required-empty-surface;
    box-shadow: inset 0 0 0 1px $required-empty-border;
    border-radius: $radius-sm;
    padding: 2px 6px;
    display: inline-block;
    min-width: 1.2em;
    text-align: center;
  }

  $format-invalid-surface: rgba($warning, 0.12);
  $format-invalid-border: rgba($warning, 0.5);
  $format-invalid-text: darken($warning, 12%);

  :deep(.format-invalid) {
    background: $format-invalid-surface !important;
    border: 1px solid $format-invalid-border !important;
    border-radius: $radius-sm;
    box-shadow: none;
    color: $format-invalid-text;

    &:hover,
    &:focus,
    &.ant-input-number-focused {
      background: $format-invalid-surface !important;
      border-color: $format-invalid-border !important;
      box-shadow: none;
    }

    input {
      border-color: transparent !important;
      color: $format-invalid-text;
    }
  }

  :deep(.task-edit-date-picker) {
    width: 100%;
    font-size: 12px;

    &.ant-picker {
      padding: 0 2px;
    }

    .ant-picker-input > input {
      font-size: 12px !important;
      padding: 0 2px;
    }
  }

  :deep(.task-edit-date-picker.format-invalid) {
    background: $format-invalid-surface !important;
    border: 1px solid $format-invalid-border !important;

    .ant-picker-input > input {
      color: $format-invalid-text;

      &::placeholder {
        color: $format-invalid-text;
      }
    }
  }

  .format-invalid-display {
    color: $format-invalid-text;
    font-weight: 600;
    background: $format-invalid-surface;
    box-shadow: inset 0 0 0 1px $format-invalid-border;
    border-radius: $radius-sm;
    padding: 2px 6px;
    display: inline-block;
    min-width: 1.2em;
    text-align: center;
  }

  .col-format-hint-icon {
    margin-left: 4px;
    color: rgba($warning, 0.85);
    font-size: 12px;
    cursor: help;
    vertical-align: -0.1em;
  }

  .format-field-cell,
  .field-with-change-hint {
    width: 100%;
    min-width: 0;
    display: flex;
    flex-direction: column;
    align-items: stretch;
    justify-content: flex-start;
  }

  :deep(.format-time-cell),
  :deep(.exception-calibrate-required-cell) {
    overflow: visible !important;
    vertical-align: top !important;
  }

  .format-hint-below {
    margin-top: 4px;
    font-size: 12px;
    line-height: 1.4;
    color: $format-invalid-text;
    white-space: normal;
  }

  .field-change-hint {
    margin-top: 1px;
    max-width: 100%;
    font-size: 9px;
    line-height: 1.2;
    font-weight: $font-weight-semibold;
    color: $danger-dark;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .format-same-time-hint {
    display: block;
    padding: 2px 6px;
    border-radius: $radius-sm;
    background: $format-invalid-surface;
    border: 1px solid $format-invalid-border;
    font-weight: $font-weight-medium;
  }

  :deep(.validation-cell-flash) {
    animation: validation-cell-flash 2.2s ease;
  }

  @keyframes validation-cell-flash {
    0%, 100% { box-shadow: none; }
    15%, 45% { box-shadow: 0 0 0 2px rgba($warning, 0.55); border-radius: $radius-sm; }
  }

  .task-image-files {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 4px 10px;
    margin-bottom: 6px;
    padding: 2px 0;
    border: none;
    background: transparent;

    &__title {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      font-weight: $font-weight-semibold;
      color: $text-secondary;
      font-size: $font-size-sm;
      white-space: nowrap;

      em {
        font-style: normal;
        font-weight: $font-weight-normal;
        color: $text-tertiary;
      }
    }

    &__list {
      list-style: none;
      margin: 0;
      padding: 0;
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 4px 12px;
      min-width: 0;
      flex: 1 1 auto;
    }

    &__item {
      margin: 0;
      min-width: 0;

      &--active .task-image-files__name {
        color: $primary;
        font-weight: $font-weight-semibold;
      }
    }

    &__link {
      display: inline-flex;
      align-items: center;
      max-width: 100%;
      padding: 0;
      border: none;
      background: transparent;
      cursor: pointer;
      text-align: left;

      &:hover .task-image-files__name,
      &:focus-visible .task-image-files__name {
        color: $primary;
        text-decoration: underline;
      }
    }

    &__name {
      font-size: $font-size-sm;
      font-weight: $font-weight-medium;
      color: $text-strong;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }

  .action-bar {
    margin-top: 28px;
    padding-top: 24px;
    border-top: 1px solid $border;
    display: flex;
    justify-content: center;
    gap: 20px;
  }

  .duplicate-expanded {
    padding: 10px 6px;
    background: $warning-light;
    border: 1px dashed $warning;
    border-radius: $radius-md;
  }

  .duplicate-expanded-title {
    font-size: $font-size-sm;
    font-weight: $font-weight-semibold;
    color: $warning-dark;
    margin-bottom: 8px;
  }

  .duplicate-expanded-list {
    overflow-x: auto;
    max-width: 100%;
    -webkit-overflow-scrolling: touch;
  }

  .duplicate-expanded-table {
    width: max-content;
    min-width: 100%;
    border-collapse: separate;
    border-spacing: 0 4px;
    font-size: $font-size-sm;

    th, td {
      border: none;
      padding: 6px 10px;
      vertical-align: top;
      text-align: left;
      white-space: normal;
      word-break: break-word;
      line-height: 1.35;
      color: $text-primary;
    }

    th {
      background: transparent;
      font-weight: $font-weight-semibold;
      color: $text-secondary;
      padding-bottom: 4px;
    }

    tbody tr {
      background: $bg-warm;
    }

    tbody td:first-child {
      border-top-left-radius: 6px;
      border-bottom-left-radius: 6px;
    }

    tbody td:last-child {
      border-top-right-radius: 6px;
      border-bottom-right-radius: 6px;
    }
  }

  .edit-table {
    :deep(.ant-table-tbody > tr.deleted-row td.row-muted-exempt-cell),
    :deep(.ant-table-tbody > tr.absent-row td.row-muted-exempt-cell) {
      font-style: normal !important;
      text-decoration: none !important;
      color: $text-secondary !important;

      &,
      & * {
        text-decoration: none !important;
        font-style: normal !important;
      }
    }
  }
}
</style>

<style lang="scss">
.task-edit-container {
  .edit-table {
    .ant-table-tbody > tr.deleted-row,
    .ant-table-tbody > tr.absent-row {
      td {
        background: #FFFFFF !important;
        background-color: #FFFFFF !important;
        color: $text-secondary !important;
      }
    }

    .row-muted-strike-text,
  .ant-input.row-muted-strike-text,
  .ant-input-number.row-muted-strike-text,
  .ant-picker.row-muted-strike-text .ant-picker-input > input,
  .ant-select.row-muted-strike-text .ant-select-selection-item {
      text-decoration: line-through !important;
      text-decoration-thickness: 2px !important;
      color: $text-secondary !important;
      font-style: italic !important;
    }

    .ant-table-tbody > tr.deleted-row .row-muted-strike-text,
    .ant-table-tbody > tr.deleted-row .ant-input.row-muted-strike-text,
    .ant-table-tbody > tr.deleted-row .ant-input-number.row-muted-strike-text,
    .ant-table-tbody > tr.deleted-row .ant-picker.row-muted-strike-text .ant-picker-input > input,
    .ant-table-tbody > tr.deleted-row .ant-select.row-muted-strike-text .ant-select-selection-item {
      text-decoration-color: $danger-dark !important;
    }

    .ant-table-tbody > tr.absent-row .row-muted-strike-text,
    .ant-table-tbody > tr.absent-row .ant-input.row-muted-strike-text,
    .ant-table-tbody > tr.absent-row .ant-input-number.row-muted-strike-text,
    .ant-table-tbody > tr.absent-row .ant-picker.row-muted-strike-text .ant-picker-input > input,
    .ant-table-tbody > tr.absent-row .ant-select.row-muted-strike-text .ant-select-selection-item {
      text-decoration-color: $warning-dark !important;
    }

    .ant-table-tbody > tr.deleted-row:hover > td,
    .ant-table-tbody > tr.absent-row:hover > td {
      background: #FFFFFF !important;
      background-color: #FFFFFF !important;
    }

    .ant-table-tbody > tr.deleted-row > td:not(.row-muted-exempt-cell) {
      color: $text-secondary !important;
      font-style: italic !important;
      text-decoration-line: line-through !important;
      text-decoration-color: $danger-dark !important;
      text-decoration-thickness: 2px !important;
    }

    .ant-table-tbody > tr.deleted-row > td:not(.row-muted-exempt-cell) s,
    .ant-table-tbody > tr.deleted-row > td:not(.row-muted-exempt-cell) del,
    .ant-table-tbody > tr.deleted-row > td:not(.row-muted-exempt-cell) .row-muted-strike-text,
    .ant-table-tbody > tr.deleted-row > td:not(.row-muted-exempt-cell) .cell-text,
    .ant-table-tbody > tr.deleted-row > td:not(.row-muted-exempt-cell) .cell-serial,
    .ant-table-tbody > tr.deleted-row > td:not(.row-muted-exempt-cell) .cell-muted,
    .ant-table-tbody > tr.deleted-row > td:not(.row-muted-exempt-cell) .format-field-cell,
    .ant-table-tbody > tr.deleted-row > td:not(.row-muted-exempt-cell) .name-cell,
    .ant-table-tbody > tr.deleted-row > td:not(.row-muted-exempt-cell) .work-hours,
    .ant-table-tbody > tr.deleted-row > td:not(.row-muted-exempt-cell) .mark-tags-cell,
    .ant-table-tbody > tr.deleted-row > td:not(.row-muted-exempt-cell) .inline-anomaly-tags,
    .ant-table-tbody > tr.deleted-row > td:not(.row-muted-exempt-cell) .signature-mark-tag,
    .ant-table-tbody > tr.deleted-row > td:not(.row-muted-exempt-cell) .ant-tag {
      color: $text-secondary !important;
      font-style: italic !important;
      text-decoration-line: line-through !important;
      text-decoration-color: $danger-dark !important;
      text-decoration-thickness: 2px !important;
    }

    .ant-table-tbody > tr.absent-row > td:not(.row-muted-exempt-cell) {
      color: $text-secondary !important;
      font-style: italic !important;
      text-decoration-line: line-through !important;
      text-decoration-color: $warning-dark !important;
      text-decoration-thickness: 2px !important;
    }

    .ant-table-tbody > tr.absent-row > td:not(.row-muted-exempt-cell) s,
    .ant-table-tbody > tr.absent-row > td:not(.row-muted-exempt-cell) del,
    .ant-table-tbody > tr.absent-row > td:not(.row-muted-exempt-cell) .row-muted-strike-text,
    .ant-table-tbody > tr.absent-row > td:not(.row-muted-exempt-cell) .cell-text,
    .ant-table-tbody > tr.absent-row > td:not(.row-muted-exempt-cell) .cell-serial,
    .ant-table-tbody > tr.absent-row > td:not(.row-muted-exempt-cell) .cell-muted,
    .ant-table-tbody > tr.absent-row > td:not(.row-muted-exempt-cell) .format-field-cell,
    .ant-table-tbody > tr.absent-row > td:not(.row-muted-exempt-cell) .name-cell,
    .ant-table-tbody > tr.absent-row > td:not(.row-muted-exempt-cell) .work-hours,
    .ant-table-tbody > tr.absent-row > td:not(.row-muted-exempt-cell) .mark-tags-cell,
    .ant-table-tbody > tr.absent-row > td:not(.row-muted-exempt-cell) .inline-anomaly-tags,
    .ant-table-tbody > tr.absent-row > td:not(.row-muted-exempt-cell) .signature-mark-tag,
    .ant-table-tbody > tr.absent-row > td:not(.row-muted-exempt-cell) .ant-tag {
      color: $text-secondary !important;
      font-style: italic !important;
      text-decoration-line: line-through !important;
      text-decoration-color: $warning-dark !important;
      text-decoration-thickness: 2px !important;
    }

    .ant-table-tbody > tr.deleted-row td.row-muted-exempt-cell,
    .ant-table-tbody > tr.absent-row td.row-muted-exempt-cell {
      font-style: normal !important;
      text-decoration: none !important;

      &,
      & * {
        text-decoration: none !important;
        font-style: normal !important;
      }
    }
    
    .ant-table-tbody > tr.blurred-row {
      td {
        background-color: $warning-light;
      }
    }
    
    .ant-table-tbody > tr.blurred-row:hover > td {
      background-color: $warning-light !important;
    }

    .ant-table-tbody > tr.parse-malformed-row {
      td {
        background-color: #fff1f0;
      }
    }

    .ant-table-tbody > tr.parse-malformed-row:hover > td {
      background-color: #ffe7e6 !important;
    }

    .ant-table-tbody > tr.manual-added-row {
      td {
        background-color: #f0f7ff;
      }
    }

    .ant-table-tbody > tr.manual-added-row:hover > td {
      background-color: #e6f4ff !important;
    }

    .field-unreadable-cell,
    :deep(.field-unreadable-cell.ant-input),
    :deep(.field-unreadable-cell.ant-input-number) {
      color: $warning;
      background: rgba(250, 173, 20, 0.08) !important;
      box-shadow: inset 0 0 0 1px rgba(250, 173, 20, 0.45);
    }

    span.field-unreadable-cell {
      display: inline-block;
      min-width: 2.5em;
      padding: 0 6px;
      border-radius: 4px;
      font-style: italic;
    }

    :deep(.required-empty.field-unreadable-cell),
    :deep(.field-unreadable-cell.required-empty.ant-input),
    :deep(.field-unreadable-cell.required-empty.ant-input-number),
    span.field-unreadable-cell.required-empty-display {
      color: $danger-dark !important;
      background: rgba(240, 101, 101, 0.11) !important;
      box-shadow: inset 0 0 0 1px rgba(240, 101, 101, 0.42) !important;
      font-style: normal;
    }
  }
}

.task-edit-submit-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 110;
  padding: 14px 24px calc(14px + env(safe-area-inset-bottom, 0px));
  background: rgba($bg-surface, 0.96);
  backdrop-filter: blur(10px);
  border-top: 1px solid $border;
  box-shadow: 0 -8px 24px rgba(15, 23, 42, 0.08);
  pointer-events: auto;

  &__inner {
    display: flex;
    justify-content: center;
    align-items: center;
    gap: 20px;
    max-width: 1200px;
    margin: 0 auto;
  }
}
</style>

<style lang="scss">
.task-edit-select-dropdown-sm {
  .ant-select-item {
    font-size: 12px !important;
    min-height: 28px;
    line-height: 28px;
  }
}
</style>
