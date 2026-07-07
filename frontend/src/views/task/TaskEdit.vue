<template>
  <div class="task-edit-container page-inner" :class="{ 'has-sticky-submit': canShowSubmitBar }">
    <div class="image-compare-layout" :class="{ 'image-compare-layout--dock-open': previewDockOpen }">
      <div class="image-compare-layout__main">
    <PageShell :title="$t('taskEdit.title')" :subtitle="taskId">
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
      />

      <div v-if="previewImagesList.length > 0" class="task-image-files">
        <div class="task-image-files__head">
          <FileImageOutlined />
          <span class="task-image-files__title">
            {{ $t('taskEdit.originalImage') }}
            <em>({{ previewImagesList.length }}{{ $t('tasks.images') }})</em>
          </span>
        </div>
        <ul class="task-image-files__list">
          <li
            v-for="(url, idx) in previewImagesList"
            :key="`${url}-${idx}`"
            class="task-image-files__item"
            :class="{ 'task-image-files__item--active': previewDockOpen && previewCurrentIndex === idx }"
          >
            <button type="button" class="task-image-files__link" @click="openImagePreview(idx)">
              <FileImageOutlined class="task-image-files__icon" />
              <span class="task-image-files__name">{{ getFileName(url) }}</span>
              <EyeOutlined class="task-image-files__view" />
            </button>
          </li>
        </ul>
      </div>
      
      <a-tabs v-model:active-key="activeTab" class="edit-tabs">
        <a-tab-pane key="edit" :tab="$t('taskEdit.editData')">
          <div
            v-if="hasTaskWorkRegion"
            class="work-region-context"
            :class="{ 'work-region-context--warning': workRegionMismatch }"
          >
            <div class="work-region-context__head">
              <span class="work-region-context__label">{{ $t('taskEdit.workRegionContextTitle') }}</span>
              <span class="work-region-context__value">{{ taskWorkRegionLabel }}</span>
              <a-tag v-if="workRegionMismatch" color="warning" class="work-region-context__badge">
                {{ $t('taskEdit.workRegionMismatchBadge', { count: workRegionMismatchCount }) }}
              </a-tag>
            </div>
            <p class="work-region-context__summary">
              {{ $t('taskEdit.workRegionContextSummary', {
                region: taskWorkRegionLabel,
                column: $t('taskEdit.countryField'),
                code: taskWorkRegionBannerCode || taskWorkRegionDisplayCode || taskWorkRegionCode || '—',
              }) }}
            </p>
            <ol class="work-region-context__steps">
              <li>{{ $t('taskEdit.workRegionContextStep1', { column: $t('taskEdit.countryField') }) }}</li>
              <li>{{ $t('taskEdit.workRegionContextStep2') }}</li>
            </ol>
          </div>
          <div class="table-toolbar">
            <a-tooltip v-if="canShowSubmitBar" :title="$t('taskEdit.addManualRowHint')">
              <a-button
                type="dashed"
                size="small"
                class="table-toolbar__add-row"
                @click="handleAddManualRecord"
              >
                <template #icon><PlusOutlined /></template>
                {{ $t('taskEdit.addManualRow') }}
              </a-button>
            </a-tooltip>
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
          <div class="duplicate-scope-bar">
            <span class="duplicate-scope-label">{{ $t('taskEdit.duplicateScopeLabel') }}</span>
            <a-radio-group
              v-model:value="duplicateScope"
              size="small"
              @change="handleDuplicateScopeChange"
            >
              <a-radio-button value="confirmed_only">{{ $t('taskEdit.duplicateScopeConfirmedOnly') }}</a-radio-button>
              <a-radio-button value="confirmed_and_processing">{{ $t('taskEdit.duplicateScopeConfirmedAndProcessing') }}</a-radio-button>
            </a-radio-group>
          </div>
          <a-alert
            v-if="submitValidationCount > 0 && !isConfirmedTask"
            type="warning"
            show-icon
            class="required-validation-banner"
          >
            <template #message>
              <span>{{ $t('taskEdit.submitValidationBanner', { count: submitValidationCount }) }}</span>
              <a-button type="link" size="small" class="required-validation-detail-btn" @click="showRequiredValidationDetail">
                {{ $t('taskEdit.requiredValidationViewDetail') }}
              </a-button>
            </template>
          </a-alert>
          <div v-if="anomalyAlertCount > 0" class="anomaly-hint">
            <div class="anomaly-summary">
              <ExclamationCircleOutlined class="anomaly-icon" />
              <span class="anomaly-text">{{ $t('home.anomalyAlert', { count: anomalyAlertCount }) }}</span>
              <a-button type="link" size="small" @click="toggleAnomalyDetail" class="anomaly-toggle">
                {{ showAnomalyDetail ? $t('home.collapse') : $t('home.expand') }}
              </a-button>
            </div>
            <div v-if="showAnomalyDetail" class="anomaly-detail-list">
              <div v-for="(alert, idx) in anomalyAlertsDetail" :key="idx" class="anomaly-item">
                <span class="anomaly-name">{{ alert.name }}</span>
                <div class="anomaly-reasons">
                  <TruncatedTag
                    v-for="group in alert.groups"
                    :key="group.category"
                    :text="group.summary"
                    :color="getAnomalyCategoryColor(group.category)"
                    size="small"
                  />
                </div>
              </div>
              <div v-if="anomalyAlertsOverflow > 0" class="anomaly-more-hint">
                {{ $t('taskEdit.anomalyAlertMore', { count: anomalyAlertsOverflow }) }}
              </div>
            </div>
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
                :resizable="isColumnResizable(column)"
                @sort="onSorterToggle"
                @resize-start="(event) => startColumnResize(column, event)"
              >
                <template #extra>
                  <a-tooltip
                    v-if="column.key === 'Pays' && hasTaskWorkRegion"
                    :title="$t('taskEdit.workRegionPaysColumnHint', { region: taskWorkRegionLabel })"
                  >
                    <QuestionCircleOutlined class="col-format-hint-icon" @click.stop />
                  </a-tooltip>
                  <a-tooltip
                    v-else-if="column.formatHintTooltipKey"
                    :title="t(column.formatHintTooltipKey)"
                  >
                    <QuestionCircleOutlined class="col-format-hint-icon" @click.stop />
                  </a-tooltip>
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
                        :options="resolveColumnFilterOptions(column, t)"
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
              <template v-if="column.key === 'serialNo'">
                <RowStrikeText :muted="isRowMuted(record)" cell-class="cell-text cell-serial">{{ globalRowSerial(index) }}</RowStrikeText>
              </template>
              <template v-if="column.key === 'anomalyReasons'">
                <template v-for="anomalyGroups in [getRecordAnomalyGroups(record)]" :key="`${record._rowKey}-anomaly`">
                  <div v-if="anomalyGroups.length > 0" class="inline-anomaly-tags">
                    <TruncatedTag
                      v-for="group in anomalyGroups"
                      :key="group.category"
                      :text="group.summary"
                      :color="getAnomalyCategoryColor(group.category)"
                      size="small"
                    />
                  </div>
                  <RowStrikeText v-else :muted="isRowMuted(record)" cell-class="cell-muted">-</RowStrikeText>
                </template>
              </template>
              <template v-if="column.key === 'PAGE_NUM'">
                <a-input
                  v-if="isRecordEditable(record)"
                  v-model:value="record.PAGE_NUM"
                  size="small"
                  :bordered="false"
                  :class="fieldInputClass(record, 'PAGE_NUM')"
                  :style="mutedStrikeStyle(record)"
                />
                <RowStrikeText v-else :muted="isRowMuted(record)" :cell-class="fieldTextClass(record, 'PAGE_NUM')">{{ displayFieldValue(record.PAGE_NUM || record.pageNum) }}</RowStrikeText>
              </template>
              <template v-if="column.key === 'EMPLOYEE_NO'">
                <RowStrikeText :muted="isRowMuted(record)" :cell-class="fieldTextClass(record, 'EMPLOYEE_NO')">{{ displayRecordField(record, 'EMPLOYEE_NO') }}</RowStrikeText>
              </template>
              <template v-if="column.key === 'NO'">
                <a-input v-if="isRecordEditable(record)" v-model:value="record.NO" size="small" :class="fieldInputClass(record, 'NO')" :style="mutedStrikeStyle(record)" :bordered="false" :placeholder="fieldUnreadablePlaceholder(record, 'NO')" @change="onReadableFieldChange(record, 'NO')" />
                <RowStrikeText v-else :muted="isRowMuted(record)" :cell-class="fieldTextClass(record, 'NO')">{{ displayRecordField(record, 'NO') }}</RowStrikeText>
              </template>
              <template v-if="column.key === 'Pays'">
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
              <template v-if="column.key === 'Entrepot'">
                <a-input v-if="isRecordEditable(record)" v-model:value="record.Entrepot" size="small" :class="fieldInputClass(record, 'Entrepot')" :style="mutedStrikeStyle(record)" :bordered="false" :placeholder="fieldUnreadablePlaceholder(record, 'Entrepot')" @change="onReadableFieldChange(record, 'Entrepot')" />
                <RowStrikeText v-else :muted="isRowMuted(record)" :cell-class="fieldTextClass(record, 'Entrepot')">{{ displayRecordField(record, 'Entrepot') }}</RowStrikeText>
              </template>
              <template v-if="column.key === 'NOM_PRENOM'">
                <div class="name-cell">
                  <a-input
                    v-if="isRecordEditable(record)"
                    v-model:value="record.NOM_PRENOM"
                    size="small"
                    :class="fieldInputClass(record, 'NOM_PRENOM')"
                    :style="mutedStrikeStyle(record)"
                    :bordered="false"
                    :placeholder="fieldUnreadablePlaceholder(record, 'NOM_PRENOM')"
                    @change="onReadableFieldChange(record, 'NOM_PRENOM')"
                  />
                  <RowStrikeText v-else :muted="isRowMuted(record)" :cell-class="fieldTextClass(record, 'NOM_PRENOM')">{{ displayRecordField(record, 'NOM_PRENOM') }}</RowStrikeText>
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
              <template v-if="column.key === 'AGENCE_INTERIMAIRE'">
                <a-input v-if="isRecordEditable(record)" v-model:value="record.AGENCE_INTERIMAIRE" size="small" :class="fieldInputClass(record, 'AGENCE_INTERIMAIRE')" :style="mutedStrikeStyle(record)" :bordered="false" :placeholder="fieldUnreadablePlaceholder(record, 'AGENCE_INTERIMAIRE')" @change="onReadableFieldChange(record, 'AGENCE_INTERIMAIRE')" />
                <RowStrikeText v-else :muted="isRowMuted(record)" :cell-class="fieldTextClass(record, 'AGENCE_INTERIMAIRE')">{{ displayRecordField(record, 'AGENCE_INTERIMAIRE') }}</RowStrikeText>
              </template>
              <template v-if="column.key === 'HORAIRES_DU_TRAVAIL'">
                <div class="format-field-cell" :id="fieldCellDomId(record, 'HORAIRES_DU_TRAVAIL')">
                  <a-tooltip v-if="isRecordEditable(record)" :title="fieldFormatTooltip(record,'HORAIRES_DU_TRAVAIL')">
                    <a-input
                      v-model:value="record.HORAIRES_DU_TRAVAIL"
                      size="small"
                      :class="fieldInputClass(record, 'HORAIRES_DU_TRAVAIL')"
                      :style="mutedStrikeStyle(record)"
                      :bordered="false"
                      :placeholder="fieldCellPlaceholder(record, 'HORAIRES_DU_TRAVAIL')"
                      @change="onReadableFieldChange(record, 'HORAIRES_DU_TRAVAIL')"
                      @blur="() => onFormatFieldBlur(record, 'HORAIRES_DU_TRAVAIL')"
                    />
                  </a-tooltip>
                  <RowStrikeText v-else :muted="isRowMuted(record)" :cell-class="fieldTextClass(record, 'HORAIRES_DU_TRAVAIL')">{{ displayRecordField(record, 'HORAIRES_DU_TRAVAIL') }}</RowStrikeText>
                  <div v-if="isFormatFieldInvalid(record, 'HORAIRES_DU_TRAVAIL')" class="format-hint-below">{{ fieldFormatTooltip(record,'HORAIRES_DU_TRAVAIL') }}</div>
                </div>
              </template>
              <template v-if="column.key === 'Date'">
                <div class="format-field-cell" :id="fieldCellDomId(record, 'Date')">
                  <a-tooltip v-if="isRecordEditable(record)" :title="fieldFormatTooltip(record,'Date')">
                    <a-date-picker
                      v-model:value="record.Date"
                      size="small"
                      class="task-edit-date-picker"
                      :class="fieldInputClass(record, 'Date')"
                      :style="mutedStrikeStyle(record)"
                      format="YYYY-MM-DD"
                      value-format="YYYY-MM-DD"
                      :bordered="false"
                      :allow-clear="true"
                      :placeholder="fieldCellPlaceholder(record, 'Date') || fieldFormatTooltip(record,'Date')"
                      @change="onReadableFieldChange(record, 'Date')"
                    />
                  </a-tooltip>
                  <RowStrikeText v-else :muted="isRowMuted(record)" :cell-class="fieldTextClass(record, 'Date')">{{ displayRecordField(record, 'Date') }}</RowStrikeText>
                  <div v-if="isFormatFieldInvalid(record, 'Date')" class="format-hint-below">{{ fieldFormatTooltip(record,'Date') }}</div>
                </div>
              </template>
              <template v-if="column.key === 'ARRIVEE'">
                <div class="format-field-cell" :id="fieldCellDomId(record, 'ARRIVEE')">
                  <a-tooltip v-if="isRecordEditable(record)" :title="fieldFormatTooltip(record,'ARRIVEE')">
                    <a-input
                      v-model:value="record.ARRIVEE"
                      size="small"
                      :class="fieldInputClass(record, 'ARRIVEE')"
                      :style="mutedStrikeStyle(record)"
                      :bordered="false"
                      :placeholder="fieldCellPlaceholder(record, 'ARRIVEE')"
                      @change="onReadableFieldChange(record, 'ARRIVEE')"
                      @input="() => onTimeFieldInput(record, 'ARRIVEE')"
                      @blur="() => onFormatFieldBlur(record, 'ARRIVEE')"
                    />
                  </a-tooltip>
                  <RowStrikeText v-else :muted="isRowMuted(record)" :cell-class="fieldTextClass(record, 'ARRIVEE')">{{ displayRecordField(record, 'ARRIVEE') }}</RowStrikeText>
                  <div v-if="showSameTimeHint(record, 'ARRIVEE')" class="format-hint-below format-same-time-hint">
                    {{ sameTimeHintText(record) }}
                  </div>
                  <div v-else-if="isFormatFieldInvalid(record, 'ARRIVEE')" class="format-hint-below">{{ fieldFormatTooltip(record,'ARRIVEE') }}</div>
                </div>
              </template>
              <template v-if="column.key === 'DEPAR'">
                <div class="format-field-cell" :id="fieldCellDomId(record, 'DEPAR')">
                  <a-tooltip v-if="isRecordEditable(record)" :title="fieldFormatTooltip(record,'DEPAR')">
                    <a-input
                      v-model:value="record.DEPAR"
                      size="small"
                      :class="fieldInputClass(record, 'DEPAR')"
                      :style="mutedStrikeStyle(record)"
                      :bordered="false"
                      :placeholder="fieldCellPlaceholder(record, 'DEPAR')"
                      @change="onReadableFieldChange(record, 'DEPAR')"
                      @input="() => onTimeFieldInput(record, 'DEPAR')"
                      @blur="() => onFormatFieldBlur(record, 'DEPAR')"
                    />
                  </a-tooltip>
                  <RowStrikeText v-else :muted="isRowMuted(record)" :cell-class="fieldTextClass(record, 'DEPAR')">{{ displayRecordField(record, 'DEPAR') }}</RowStrikeText>
                  <div v-if="showSameTimeHint(record, 'DEPAR')" class="format-hint-below format-same-time-hint">
                    {{ sameTimeHintText(record) }}
                  </div>
                  <div v-else-if="isFormatFieldInvalid(record, 'DEPAR')" class="format-hint-below">{{ fieldFormatTooltip(record,'DEPAR') }}</div>
                </div>
              </template>
              <template v-if="column.key === 'PAUSE'">
                <a-input-number v-if="isRecordEditable(record)" v-model:value="record.PAUSE" size="small" :class="fieldInputClass(record, 'PAUSE')" :style="mutedStrikeStyle(record)" :bordered="false" :controls="false" :min="0" :precision="0" style="width: 100%" @blur="() => normalizeRecordPauseOnBlur(record)" />
                <RowStrikeText v-else :muted="isRowMuted(record)" :cell-class="fieldTextClass(record, 'PAUSE')">{{ formatPauseDisplay(record.PAUSE) }}</RowStrikeText>
              </template>
              <template v-if="column.key === 'SIGNATURE'">
                <a-select
                  v-if="isRecordEditable(record)"
                  v-model:value="record.SIGNATURE"
                  size="small"
                  :bordered="false"
                  class="signature-mark-select"
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
              <template v-if="column.key === 'Observations'">
                <a-input v-if="isRecordEditable(record)" v-model:value="record.Observations" size="small" :class="fieldInputClass(record, 'Observations')" :style="mutedStrikeStyle(record)" :bordered="false" :placeholder="fieldUnreadablePlaceholder(record, 'Observations')" @change="onReadableFieldChange(record, 'Observations')" />
                <RowStrikeText v-else :muted="isRowMuted(record)" :cell-class="fieldTextClass(record, 'Observations')">{{ displayRecordField(record, 'Observations') }}</RowStrikeText>
              </template>
              <template v-if="column.key === 'SmartMark'">
                <div class="mark-tags-cell">
                  <template v-for="tag in getRecordMarkTags(record)" :key="tag.key">
                    <a-popover
                      v-if="tag.showCalibrationHistory"
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
                      <a-tag :color="tag.color" size="small" class="mark-tag calibration-tag">
                        {{ tag.label }}
                      </a-tag>
                    </a-popover>
                    <a-tag v-else :color="tag.color" size="small" class="mark-tag">{{ tag.label }}</a-tag>
                  </template>
                </div>
              </template>
              <template v-if="column.key === 'workHours'">
                <RowStrikeText :muted="isRowMuted(record)" cell-class="work-hours">{{ calculateWorkHours(record) }}</RowStrikeText>
              </template>
              <template v-if="column.key === 'action'">
                <div class="table-action-cell table-action-cell--icons-mixed">
                  <span class="table-action-cell__slot">
                    <a-button
                      v-if="isConfirmedTask && canCalibrateRecord && !record.isDeleted"
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
        </a-tab-pane>
      </a-tabs>
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
    :images="previewImagesList"
    :initial-index="previewCurrentIndex"
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
import { DeleteOutlined, UndoOutlined, ExclamationCircleOutlined, FileImageOutlined, EyeOutlined, UploadOutlined, DownloadOutlined, QuestionCircleOutlined, LoadingOutlined, PlusOutlined } from '@ant-design/icons-vue'
import { getTaskDetail, getTaskProgress, confirmTask, deleteTask, retryFeishuSync, calibrateTaskRecord } from '@/api/task'
import { useAuthStore } from '@/stores/auth'
import { resolveTaskImageUrls, fileNameFromImageUrl } from '@/utils/imageUrl'
import StatOverview from '@/components/StatOverview.vue'
import PageShell from '@/components/PageShell.vue'
import TruncatedTag from '@/components/TruncatedTag.vue'
import RowStrikeText from '@/components/RowStrikeText.vue'
import ImagePreviewModal from '@/components/ImagePreviewModal.vue'
import ImageCompareDockShell from '@/components/ImageCompareDockShell.vue'
import { useImageComparePreview } from '@/composables/useImageComparePreview'
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
import { useTaskEditConfirmValidation } from '@/composables/useTaskEditConfirmValidation'
import { useTaskEditRecordDisplay } from '@/composables/useTaskEditRecordDisplay'
import { getFormatHintKeys, isFormatHintField } from '@/utils/fieldFormatHints'
import { isArrivalDepartureSameTime } from '@/utils/recordFieldFormatRules'
import {
  normalizeWorkerNo,
  normalizePersonName,
  normalizeLabelText,
} from '@/utils/recognizedTextNormalizer'
import { normalizeDate } from '@/utils/recognizedDateNormalizer'
import { loadNightShiftRules } from '@/utils/nightShiftRules'
import { isNonTimeFieldLabel } from '@/utils/recognizedTimeNormalizer'
import { createManualTaskRecord } from '@/utils/manualTaskRecord'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const taskId = computed(() => route.params.taskId)
const activeTab = ref('edit')
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
  refreshDuplicateDecorations,
  fetchConfirmedDuplicateHints,
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
  getRecordAnomalyGroups,
  getRowClassName,
  getMarkColor,
  getRowTypeLabel,
  getRowTypeDotClass,
  getAnomalyTagColor,
  getAnomalyCategoryColor,
  getAnomalyTagClass,
  countAnomalyRecords,
  buildAnomalyAlertsSlice,
  clearRowCache,
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

const fieldInputClass = (record, field) => {
  const requiredEmpty = isRequiredFieldEmpty(record, field)
  const formatInvalid = requiredInputClass(record, field)['format-invalid']
  const unreadable = isFieldUnreadable(record, field) && !requiredEmpty && !formatInvalid
  const classes = {
    ...requiredInputClass(record, field),
    'field-unreadable-cell': unreadable,
  }
  if (isRowMuted(record)) {
    classes['row-muted-strike-text'] = true
  }
  return classes
}

const fieldTextClass = (record, field) => {
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
  if (isRowMuted(record)) {
    classes = { ...classes, 'row-muted-strike-text': true }
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
  if ((field === 'ARRIVEE' || field === 'DEPAR') && isArrivalDepartureSameTime(record)) {
    return sameTimeHintText(record)
  }
  const keys = getFormatHintKeys(field, { record, isSameArrivalDeparture: isArrivalDepartureSameTime })
  return keys ? t(keys.tooltip) : ''
}

const showSameTimeHint = (record, field) => (
  (field === 'ARRIVEE' || field === 'DEPAR') && isArrivalDepartureSameTime(record)
)

const fieldCellDomId = (record, field) => {
  if (!isFormatHintField(field)) return undefined
  return `field-cell-${getRowKey(record)}-${field}`
}

const customTableRow = (record) => ({
  id: `field-row-${getRowKey(record)}`,
})

const onTimeFieldInput = (record, field) => {
  clearRowCache()
  if (field === 'ARRIVEE' || field === 'DEPAR') {
    scheduleAnomalyCountUpdate(true)
    scheduleRequiredMissingCountUpdate(true)
  }
}

const onFormatFieldBlur = (record, field) => {
  clearRowCache()
  scheduleRequiredMissingCountUpdate(true)
  if (field === 'ARRIVEE' || field === 'DEPAR' || field === 'HORAIRES_DU_TRAVAIL' || field === 'Date') {
    scheduleAnomalyCountUpdate(true)
  }
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

const applyFieldNormalization = (record, field) => {
  if (!record) return
  if (field === 'NO') record.NO = normalizeWorkerNo(record.NO)
  if (field === 'NOM_PRENOM') record.NOM_PRENOM = normalizePersonName(record.NOM_PRENOM)
  if (field === 'Entrepot') record.Entrepot = normalizeLabelText(record.Entrepot)
  if (field === 'AGENCE_INTERIMAIRE') record.AGENCE_INTERIMAIRE = normalizeLabelText(record.AGENCE_INTERIMAIRE)
  if (field === 'Date') record.Date = normalizeDate(record.Date)
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
  clearRowCache()
  if (field === 'ARRIVEE' || field === 'DEPAR') {
    scheduleAnomalyCountUpdate(true)
  }
}

const getRowAnomalyReasons = (record) => getRecordAnomalyReasons(record)

const rawData = ref('')
const showAnomalyDetail = ref(false)
const ANOMALY_DETAIL_LIMIT = 20
const VALIDATION_BANNER_DEBOUNCE_MS = 450
const previewImagesList = ref([])
const previewCurrentIndex = ref(0)
const {
  dockOpen: previewDockOpen,
  fullscreenOpen: previewFullscreenOpen,
  openPreview: openPreviewPanel,
  openFullscreen: openPreviewFullscreen,
} = useImageComparePreview()
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

const anomalyAlertCount = ref(0)
let anomalyCountDebounceTimer = null
const scheduleAnomalyCountUpdate = (immediate = false) => {
  if (anomalyCountDebounceTimer) {
    window.clearTimeout(anomalyCountDebounceTimer)
    anomalyCountDebounceTimer = null
  }
  if (immediate) {
    anomalyAlertCount.value = countAnomalyRecords(records.value)
    return
  }
  anomalyCountDebounceTimer = window.setTimeout(() => {
    anomalyCountDebounceTimer = null
    anomalyAlertCount.value = countAnomalyRecords(records.value)
  }, VALIDATION_BANNER_DEBOUNCE_MS)
}

const anomalyAlertsDetail = computed(() => {
  if (!showAnomalyDetail.value) return []
  return buildAnomalyAlertsSlice(records.value, ANOMALY_DETAIL_LIMIT)
})

const anomalyAlertsOverflow = computed(() => {
  if (!showAnomalyDetail.value) return 0
  return Math.max(0, anomalyAlertCount.value - anomalyAlertsDetail.value.length)
})

const toggleAnomalyDetail = () => {
  const next = !showAnomalyDetail.value
  showAnomalyDetail.value = next
  if (next) {
    scheduleAnomalyCountUpdate(true)
  }
}

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
const ROW_MUTED_BG = '#F5F3F7'
const ROW_MUTED_TEXT = '#73707F'
const ROW_MUTED_STRIKE_DELETED = '#D94040'
const ROW_MUTED_STRIKE_ABSENT = '#B8860B'

const isRecordDeleted = (record) => Boolean(record?.isDeleted || record?.deleted)

const resolveRowMutedStrikeColor = (record) => (
  isRecordDeleted(record) ? ROW_MUTED_STRIKE_DELETED : ROW_MUTED_STRIKE_ABSENT
)

const isRowMuted = (record) => isRecordDeleted(record) || isAbsentRow(record)

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
  const wrapClass = wrapKeys.includes(columnKey) ? 'cell-wrap' : ''
  if (!wrapClass && !props.class) return props
  const cls = [props.class, wrapClass].filter(Boolean).join(' ')
  return { ...props, class: cls || undefined }
}

const cellStyle = (record, rowIndex, columnKey) => {
  if (!record) return {}
  if (isRowMuted(record)) {
    const exempt = ROW_MUTED_EXEMPT_KEYS.has(columnKey)
    const style = {
      backgroundColor: ROW_MUTED_BG,
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
  if ((fieldKey === 'ARRIVEE' || fieldKey === 'DEPAR') && isArrivalDepartureSameTime(record)) {
    return mergeCellProps({ class: 'format-time-cell format-time-cell--invalid' }, columnKey)
  }
  if (fieldKey && isConfiguredRequiredField(fieldKey) && isRequiredFieldEmpty(record, fieldKey)) {
    return mergeCellProps({ class: 'required-field-cell' }, columnKey)
  }
  if (fieldKey === 'Pays' && taskWorkRegionCode.value && isPaysRegionMismatch(record.Pays, taskWorkRegionCode.value)) {
    return mergeCellProps({ class: 'work-region-mismatch-cell' }, columnKey)
  }
  if ((record?.SmartMark || '').includes('模糊')) {
    return mergeCellProps({ style: { backgroundColor: '#FFF9EC' } }, columnKey)
  }
  return mergeCellProps({}, columnKey)
}

const baseColumns = computed(() => buildRecognitionTableColumns(t, {
  requiredFieldKeys: confirmRequiredFields.value,
  cellStyle,
  includeWorkHours: true,
  searchFields: true,
  fixedAction: true,
  actionColumnWidth: 88,
}))
const { columns: sortedColumns, onSorterToggle, sortRows } = useTableColumnSort(baseColumns, { customHeader: true })

const filteredRecords = computed(() => {
  const active = Object.entries(headerFilters.value).filter(([, v]) => {
    if (Array.isArray(v)) return v.length > 0
    if (v && typeof v === 'object') return Boolean(v.from?.trim() || v.to?.trim())
    return String(v || '').trim() !== ''
  })
  if (!active.length) return records.value
  return records.value.filter((row) => active.every(([field, value]) => {
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

const globalRowSerial = (index) => index + 1

const resetTableColumnsLock = () => {
  columnsLocked.value = false
  lockedSizedColumns.value = []
}

const { columns: sizedColumns } = useAutoSizedColumns(sortedColumns, tableRecords, {
  enabled: computed(() => !columnsLocked.value),
  actionWidth: isConfirmedTask.value && canCalibrateRecord.value ? 88 : 50,
  getCellSample: (col, record) => {
    if (col.key === 'workHours') return calculateWorkHours(record)
    if (col.key === 'anomalyReasons') {
      return getRecordAnomalyGroups(record).map((group) => group.summary).join('; ')
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
} = useTableColumnResize('task-edit', effectiveSizedColumns)

watch(
  () => sizedColumns.value,
  (cols) => {
    if (columnsLocked.value || !cols?.length || !records.value.length) return
    lockedSizedColumns.value = cols.map((col) => ({ ...col }))
    columnsLocked.value = true
  },
  { immediate: true },
)

const {
  frozenColumns: columns,
  hiddenKeys,
  frozenKeys,
  configurableColumns,
  setHiddenKeys,
  setFrozenKeys,
  showAllColumns,
  clearFrozenKeys,
} = useColumnFreeze('task-edit', resizedColumns, { defaultFrozen: ['serialNo', 'PAGE_NUM', 'NO'] })

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

const NARROW_HEADER_COLUMN_KEYS = new Set(['NO', 'PAGE_NUM', 'Pays', 'PAUSE', 'EMPLOYEE_NO'])

const isNarrowHeaderColumn = (column) => NARROW_HEADER_COLUMN_KEYS.has(column?.key)

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
    showAnomalyDetail.value = false
    
    const dataPayload = resolveTaskRecordsJson(task.value)
    if (dataPayload) {
      const parsedRecords = JSON.parse(dataPayload)
      records.value = parsedRecords.map((record, idx) => {
        const normalized = prepareRecordPlaceholders(normalizeRecordPause({
          ...record,
          isDeleted: Boolean(record.isDeleted || record.deleted),
          _rowKey: record._rowKey || `${taskId.value}-${idx}`,
          _baseName: String(sanitizeFieldValue(record.NOM_PRENOM) || '').trim(),
          _nameAutoNumbered: false,
          _duplicateConfirmedUnique: false
        }))
        const signatureMark = computeSignatureMark(normalized)
        normalized.SIGNATURE = signatureMark
        normalized.CHECKER = signatureMark
        return normalized
      })
      refreshDuplicateDecorations()
      await fetchConfirmedDuplicateHints()
      scheduleRequiredMissingCountUpdate(true)
      scheduleAnomalyCountUpdate(true)
    }
    rawData.value = task.value.aiRawOutput || ''
    
    previewImagesList.value = await resolveTaskImageUrls(task.value.imageUrls, task.value.fileKey)
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
    const token = localStorage.getItem('attendance_token')
    const locale = encodeURIComponent(currentExportLocale())
    const response = await fetch(`${API_BASE_PATH}/local/export/${taskId.value}/xlsx?locale=${locale}`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {}
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
  previewCurrentIndex.value = Math.min(Math.max(index, 0), Math.max(previewImagesList.value.length - 1, 0))
  openPreviewPanel()
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
  records.value = [...records.value, normalized]
  clearRowCache()
  refreshDuplicateDecorations()
  scheduleRequiredMissingCountUpdate(true)
  scheduleAnomalyCountUpdate(true)
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
    clearRowCache()
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
  records.value.splice(index, 1, record)
  clearRowCache()
  refreshDuplicateDecorations()
}

const calculateWorkHours = (record) => {
  if (record?.isDeleted || isAbsentRow(record)) {
    return '-'
  }
  
  const arriveTime = record?.ARRIVEE
  const departTime = record?.DEPAR
  const pauseMinutes = normalizePauseMinutes(record?.PAUSE)
  
  if (!arriveTime || !departTime || arriveTime === '???' || departTime === '???') {
    return '-'
  }
  
  const arriveMinutes = parseTimeToMinutes(arriveTime)
  const departMinutes = parseTimeToMinutes(departTime)
  
  if (arriveMinutes === null || departMinutes === null) {
    return '-'
  }
  
  let totalMinutes = departMinutes - arriveMinutes
  if (totalMinutes < 0) {
    totalMinutes += 24 * 60
  }
  
  const pause = (pauseMinutes !== null && pauseMinutes !== undefined && pauseMinutes !== '') ? Number(pauseMinutes) : 0
  const workMinutes = totalMinutes - pause
  
  if (workMinutes < 0) {
    return '-'
  }
  
  const workHours = (workMinutes / 60).toFixed(2)
  return workHours
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

const handleSubmit = async () => {
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

let duplicateDebounceTimer = null

onUnmounted(() => {
  isComponentMounted = false
  expandedDuplicateRowKeys.value = []
  if (duplicateDebounceTimer) window.clearTimeout(duplicateDebounceTimer)
  if (requiredValidationDebounceTimer) window.clearTimeout(requiredValidationDebounceTimer)
  if (anomalyCountDebounceTimer) window.clearTimeout(anomalyCountDebounceTimer)
  tableLoadMoreObserver?.disconnect()
  tableLoadMoreObserver = null
  clearSyncPoll()
})

watch(taskId, () => {
  if (isComponentMounted) {
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
  'SmartMark', 'Mark', 'isDeleted', '_duplicateConfirmedUnique', '_unreadableFields',
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
  if (duplicateDebounceTimer) window.clearTimeout(duplicateDebounceTimer)
  duplicateDebounceTimer = window.setTimeout(() => {
    duplicateDebounceTimer = null
    refreshDuplicateDecorations()
  }, 300)
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

  .record-count {
    font-size: 13px;
    color: $primary;
    background: $primary-light;
    padding: 4px 12px;
    border-radius: $radius-xl;
    border: 1px solid $border-accent;
  }

  .edit-card {
    border-radius: $radius-lg;
    box-shadow: $shadow-xs;
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

  .edit-tabs {
    :deep(.ant-tabs-nav) {
      margin-bottom: 20px;
    }
  }

  .cell-text {
    font-size: 13px;
    color: $text-primary;
  }

  .mark-tag {
    font-size: $font-size-sm;
    border-radius: 4px;
  }

  .anomaly-hint {
    margin-bottom: 20px;
    padding: 14px 18px;
    background: $warning-light;
    border-radius: 10px;
    border-left: 4px solid $warning;

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
      padding-top: 14px;
      border-top: 1px solid rgba(60, 60, 67, 0.12);

      .anomaly-more-hint {
        padding-top: 8px;
        font-size: 12px;
        color: $text-secondary;
      }

      .anomaly-item {
        display: flex;
        align-items: center;
        gap: 12px;
        padding: 8px 0;
        font-size: 13px;

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
        }

        .anomaly-reasons {
          display: flex;
          gap: 8px;
          flex-wrap: wrap;
          align-items: center;
          min-width: 0;
          flex: 1;
        }
      }
    }
  }

  .table-toolbar {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: 10px;
    margin-bottom: 12px;

    &__add-row {
      margin-right: auto;
    }
  }

  .duplicate-scope-bar {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 12px;
  }

  .work-region-context {
    margin-bottom: 14px;
    padding: 12px 14px;
    border-radius: $radius-md;
    border: 1px solid rgba($primary-color, 0.12);
    background: rgba($primary-color, 0.03);

    &--warning {
      border-color: rgba($warning-color, 0.35);
      background: rgba($warning-color, 0.06);
    }

    &__head {
      display: flex;
      align-items: center;
      flex-wrap: wrap;
      gap: 8px;
      margin-bottom: 6px;
    }

    &__label {
      font-size: $font-size-sm;
      font-weight: $font-weight-semibold;
      color: $text-secondary;
    }

    &__value {
      font-size: $font-size-sm;
      font-weight: $font-weight-semibold;
      color: $text-primary;
    }

    &__badge {
      margin: 0;
    }

    &__summary {
      margin: 0 0 8px;
      font-size: $font-size-sm;
      color: $text-secondary;
      line-height: 1.55;
    }

    &__steps {
      margin: 0;
      padding-left: 1.2em;
      font-size: $font-size-sm;
      color: $text-tertiary;
      line-height: 1.6;

      li + li {
        margin-top: 2px;
      }
    }
  }

  :deep(.work-region-mismatch-cell) {
    background: rgba($warning-color, 0.1) !important;
  }

  :deep(.pays-country-select) {
    width: 100%;
    min-width: 0;

    &.ant-select-disabled .ant-select-selector {
      color: inherit;
      background: transparent;
      cursor: default;
    }
  }

  .duplicate-scope-label {
    font-size: $font-size-sm;
    color: $text-secondary;
    font-weight: $font-weight-semibold;
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

    .cell-muted {
      color: $text-tertiary;
    }

    :deep(.ant-table) {
      border-radius: 10px;
      border: 1px solid $border;
    }

    :deep(.ant-table-thead > tr > th) {
      overflow: visible;
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
      padding: 12px 16px !important;
      background: transparent;
    }

    :deep(.ant-table-tbody > tr > td) {
      border-bottom: 1px solid $border;
    }

    :deep(.ant-table-tbody > tr:hover > td) {
      background: $bg-muted !important;
    }

    :deep(.ant-input) {
      font-size: 13px;
      padding: 4px 8px;
      border-radius: $radius-sm;
      background: transparent;
      transition: all $duration-base $ease-smooth;

      &:focus, &:hover {
        background: $bg-surface;
        box-shadow: 0 0 0 2px rgba($primary, 0.15);
      }
    }

    :deep(.ant-input-number) {
      font-size: 13px;

      .ant-input-number-input {
        padding: 4px 8px;
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
    margin-bottom: 12px;

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

    &.ant-picker {
      padding: 0 4px;
    }

    .ant-picker-input > input {
      font-size: 12px;
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

  .format-field-cell {
    width: 100%;
    min-width: 0;
  }

  :deep(.format-time-cell) {
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
    margin-bottom: $space-4;
    padding: $space-3 $space-4;
    border-radius: $radius-lg;
    border: 1px solid $border-light;
    background: $bg-muted;

    &__head {
      display: flex;
      align-items: center;
      gap: $space-2;
      margin-bottom: $space-2;
      color: $text-secondary;
      font-size: $font-size-sm;
    }

    &__title {
      font-weight: $font-weight-semibold;
      color: $text-strong;
      font-size: $font-size-base;

      em {
        font-style: normal;
        font-weight: $font-weight-normal;
        color: $text-secondary;
        margin-left: 4px;
      }
    }

    &__list {
      list-style: none;
      margin: 0;
      padding: 0;
      display: flex;
      flex-direction: column;
      gap: $space-2;
    }

    &__item {
      margin: 0;

      &--active .task-image-files__link {
        border-color: $primary;
        box-shadow: 0 0 0 2px rgba($primary, 0.16);
        background: rgba($primary, 0.04);
      }
    }

    &__link {
      width: 100%;
      display: flex;
      align-items: center;
      gap: $space-3;
      padding: $space-3 $space-4;
      border: 1px solid $border;
      border-radius: $radius-md;
      background: $bg-surface;
      cursor: pointer;
      text-align: left;
      transition: border-color $duration-fast, box-shadow $duration-fast;

      &:hover {
        border-color: $primary;
        box-shadow: 0 2px 8px rgba($primary, 0.1);
      }
    }

    &__icon {
      font-size: 18px;
      color: $primary;
      flex-shrink: 0;
    }

    &__name {
      flex: 1;
      min-width: 0;
      font-size: $font-size-base;
      font-weight: $font-weight-medium;
      color: $text-strong;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    &__view {
      font-size: 16px;
      color: $text-tertiary;
      flex-shrink: 0;
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
        background-color: $bg-muted !important;
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
      background-color: $bg-muted !important;
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
