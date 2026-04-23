import sys
import os
import json
import threading
from datetime import datetime

import numpy as np
from PyQt5.QtWidgets import (
    QApplication, QMainWindow, QWidget, QVBoxLayout, QHBoxLayout,
    QLabel, QPushButton, QTableWidget, QTableWidgetItem, QFileDialog,
    QProgressBar, QGroupBox, QTextEdit, QSplitter, QMessageBox,
    QHeaderView, QAbstractItemView
)
from PyQt5.QtCore import Qt, QMimeData, pyqtSignal, QObject, pyqtSlot
from PyQt5.QtGui import QDragEnterEvent, QDropEvent, QColor, QPalette

import librosa
from PyQt5.QtGui import QPixmap, QPainter, QPen, QBrush, QColor, QFont
from PyQt5.QtCore import QRectF


class WaveformWidget(QWidget):
    """波形显示控件"""

    def __init__(self, parent=None):
        super().__init__(parent)
        self.audio_data = None
        self.sample_rate = 22050
        self.beat_times = []
        self.downbeat_times = []
        self.current_position = 0
        self.setMinimumHeight(120)
        self.setAcceptDrops(False)

    def set_audio_data(self, data, sr):
        self.audio_data = data
        self.sample_rate = sr
        self.update()

    def set_beats(self, beats, downbeats):
        self.beat_times = beats
        self.downbeat_times = downbeats
        self.update()

    def set_position(self, pos):
        self.current_position = pos
        self.update()

    def paintEvent(self, event):
        if self.audio_data is None:
            painter = QPainter(self)
            painter.fillRect(self.rect(), QPalette.Base)
            painter.drawText(self.rect(), Qt.AlignCenter, "拖拽音频文件到此处")
            return

        painter = QPainter(self)
        painter.setRenderHint(QPainter.Antialiasing)

        w, h = self.width(), self.height()
        center_y = h // 2

        # 降采样绘制波形
        step = max(1, len(self.audio_data) // w)
        samples = [self.audio_data[i*step] for i in range(min(w, len(self.audio_data)//step))]
        samples = np.abs(samples)

        # 绘制波形
        painter.setPen(QColor(100, 150, 200))
        for i, s in enumerate(samples):
            amp = int(s * center_y * 0.9)
            painter.drawLine(i, center_y - amp, i, center_y + amp)

        # 绘制beat线（绿色）
        if self.beat_times:
            duration = len(self.audio_data) / self.sample_rate
            painter.setPen(QColor(0, 200, 0))
            for bt in self.beat_times:
                x = int(bt / duration * w)
                painter.drawLine(x, 0, x, h)

        # 绘制downbeat线（红色）
        if self.downbeat_times:
            duration = len(self.audio_data) / self.sample_rate
            painter.setPen(QColor(200, 0, 0))
            for dbt in self.downbeat_times:
                x = int(dbt / duration * w)
                painter.drawLine(x, 0, x, h)

        # 绘制当前播放位置
        if self.current_position > 0:
            duration = len(self.audio_data) / self.sample_rate
            x = int(self.current_position / duration * w)
            painter.setPen(QColor(255, 200, 0))
            painter.drawLine(x, 0, x, h)


class AudioAnalyzer(QObject):
    """音频分析器"""

    progress_updated = pyqtSignal(int, str)
    analysis_complete = pyqtSignal(dict)
    analysis_error = pyqtSignal(str)

    def __init__(self):
        super().__init__()

    def analyze(self, file_path):
        try:
            self.progress_updated.emit(10, "加载音频...")
            y, sr = librosa.load(file_path, sr=None)

            # 获取音频时长
            duration = librosa.get_duration(y=y, sr=sr)
            file_size = os.path.getsize(file_path)

            self.progress_updated.emit(30, "检测节拍...")
            # 使用librosa检测beat (固定BPM)
            tempo, beat_frames = librosa.beat.beat_track(y=y, sr=sr, units='frames')
            beat_times = librosa.frames_to_time(beat_frames, sr=sr)

            self.progress_updated.emit(50, "检测downbeat...")
            # 检测小节首拍
            tempo_dynamic = librosa.feature.tempo(y=y, sr=sr, aggregate=None)
            _, downbeat_frames = librosa.beat.beat_track(
                y=y, sr=sr, units='frames',
                bpm=tempo_dynamic
            )

            # 简化：每4拍一个downbeat
            downbeat_indices = [i for i in range(0, len(beat_times), 4)]
            downbeat_times = [beat_times[i] for i in downbeat_indices if i < len(beat_times)]

            self.progress_updated.emit(70, "检测onset...")
            # 检测所有onset (敲击点)
            onset_frames = librosa.onset.onset_detect(y=y, sr=sr, backtrack=True)
            onset_times = librosa.frames_to_time(onset_frames, sr=sr)

            self.progress_updated.emit(85, "检测时变BPM...")
            # 时变BPM检测
            tempo_analysis = []
            window_size = int(sr * 10)  # 10秒窗口
            hop_size = int(sr * 1)  # 1秒步进

            for start in range(0, len(y), hop_size):
                end = min(start + window_size, len(y))
                if end - start < sr * 2:
                    continue

                window_tempo, _ = librosa.beat.beat_track(
                    y=y[start:end], sr=sr, units='time'
                )
                time_sec = start / sr

                if len(tempo_analysis) == 0 or abs(window_tempo - tempo_analysis[-1]['bpm']) > 5:
                    tempo_analysis.append({
                        'time': time_sec,
                        'bpm': window_tempo
                    })

            self.progress_updated.emit(100, "完成")

            # 生成结果
            result = {
                'file_path': file_path,
                'file_name': os.path.basename(file_path),
                'file_size': file_size,
                'duration': duration,
                'sample_rate': sr,
                'fixed_bpm': float(tempo),
                'tempo_variations': tempo_analysis,
                'beat_times': beat_times.tolist() if isinstance(beat_times, np.ndarray) else beat_times,
                'downbeat_times': downbeat_times,
                'onset_times': onset_times.tolist() if isinstance(onset_times, np.ndarray) else onset_times,
                'waveform': y,
                'analysis_time': datetime.now().isoformat()
            }

            self.analysis_complete.emit(result)

        except Exception as e:
            self.analysis_error.emit(str(e))


class MainWindow(QMainWindow):
    """主窗口"""

    def __init__(self):
        super().__init__()
        self.audio_file = None
        self.analysis_result = None
        self.analyzer = AudioAnalyzer()

        self.init_ui()

    def init_ui(self):
        self.setWindowTitle("音游谱面制作工具 - 音频分析器")
        self.setGeometry(100, 100, 1200, 800)

        # 中央部件
        central = QWidget()
        self.setCentralWidget(central)
        main_layout = QVBoxLayout(central)

        # 拖拽区域
        self.drag_label = QLabel("拖拽音频文件到此处\n支持 MP3, WAV, FLAC, OGG")
        self.drag_label.setAlignment(Qt.AlignCenter)
        self.drag_label.setStyleSheet("""
            QLabel {
                border: 3px dashed #888;
                border-radius: 10px;
                padding: 50px;
                font-size: 16px;
                color: #666;
                background: #f5f5f5;
            }
            QLabel:hover {
                border-color: #4a9eff;
                background: #e8f4ff;
            }
        """)
        self.drag_label.setAcceptDrops(True)
        main_layout.addWidget(self.drag_label)

        # 文件信息区域
        info_group = QGroupBox("文件信息")
        info_layout = QHBoxLayout(info_group)

        self.lbl_filename = QLabel("文件: 未加载")
        self.lbl_duration = QLabel("时长: --")
        self.lbl_sr = QLabel("采样率: --")
        self.lbl_bpm = QLabel("BPM: --")

        info_layout.addWidget(self.lbl_filename)
        info_layout.addWidget(self.lbl_duration)
        info_layout.addWidget(self.lbl_sr)
        info_layout.addWidget(self.lbl_bpm)
        info_layout.addStretch()

        main_layout.addWidget(info_group)

        # 波形显示
        self.waveform_widget = WaveformWidget()
        main_layout.addWidget(self.waveform_widget)

        # 进度条
        self.progress = QProgressBar()
        self.progress.setVisible(False)
        main_layout.addWidget(self.progress)

        # 控制按钮
        btn_layout = QHBoxLayout()

        self.btn_open = QPushButton("打开音频")
        self.btn_open.clicked.connect(self.open_file)
        btn_layout.addWidget(self.btn_open)

        self.btn_analyze = QPushButton("开始分析")
        self.btn_analyze.clicked.connect(self.start_analysis)
        self.btn_analyze.setEnabled(False)
        btn_layout.addWidget(self.btn_analyze)

        self.btn_export_json = QPushButton("导出JSON")
        self.btn_export_json.clicked.connect(lambda: self.export_data('json'))
        self.btn_export_json.setEnabled(False)
        btn_layout.addWidget(self.btn_export_json)

        self.btn_export_csv = QPushButton("导出CSV")
        self.btn_export_csv.clicked.connect(lambda: self.export_data('csv'))
        self.btn_export_csv.setEnabled(False)
        btn_layout.addWidget(self.btn_export_csv)

        self.btn_export_smk = QPushButton("导出SMK(谱面)")
        self.btn_export_smk.clicked.connect(lambda: self.export_data('smk'))
        self.btn_export_smk.setEnabled(False)
        btn_layout.addWidget(self.btn_export_smk)

        btn_layout.addStretch()

        main_layout.addLayout(btn_layout)

        # 结果表格
        result_group = QGroupBox("分析结果")
        result_layout = QVBoxLayout(result_group)

        # 创建表格
        self.table_beats = QTableWidget()
        self.table_beats.setColumnCount(4)
        self.table_beats.setHorizontalHeaderLabels(["序号", "时间(秒)", "时间(ms)", "类型"])
        self.table_beats.horizontalHeader().setSectionResizeMode(QHeaderView.Stretch)
        self.table_beats.setSelectionBehavior(QAbstractItemView.SelectRows)
        self.table_beats.setMaximumHeight(200)

        result_layout.addWidget(self.table_beats)

        # 统计信息
        self.stats_text = QTextEdit()
        self.stats_text.setReadOnly(True)
        self.stats_text.setMaximumHeight(100)
        result_layout.addWidget(self.stats_text)

        main_layout.addWidget(result_group)

        # 连接信号
        self.analyzer.progress_updated.connect(self.on_progress)
        self.analyzer.analysis_complete.connect(self.on_analysis_complete)
        self.analyzer.analysis_error.connect(self.on_analysis_error)

        # 安装事件过滤器
        self.drag_label.installEventFilter(self)

    def eventFilter(self, obj, event):
        if obj == self.drag_label:
            if event.type() == event.DragEnter:
                event.accept()
                return True
            elif event.type() == event.Drop:
                self.handle_drop(event)
                return True
        return super().eventFilter(obj, event)

    def handle_drop(self, event):
        mime_data = event.mimeData()
        if mime_data.hasUrls():
            urls = mime_data.urls()
            if urls:
                file_path = urls[0].toLocalFile()
                self.load_file(file_path)

    def open_file(self):
        file_path, _ = QFileDialog.getOpenFileName(
            self, "选择音频文件", "",
            "音频文件 (*.mp3 *.wav *.flac *.ogg *.m4a)"
        )
        if file_path:
            self.load_file(file_path)

    def load_file(self, file_path):
        if not os.path.exists(file_path):
            QMessageBox.warning(self, "错误", "文件不存在")
            return

        ext = os.path.splitext(file_path)[1].lower()
        if ext not in ['.mp3', '.wav', '.flac', '.ogg', '.m4a']:
            QMessageBox.warning(self, "错误", "不支持的文件格式")
            return

        self.audio_file = file_path
        self.lbl_filename.setText(f"文件: {os.path.basename(file_path)}")

        # 重置UI
        self.table_beats.setRowCount(0)
        self.stats_text.clear()
        self.waveform_widget.audio_data = None
        self.waveform_widget.update()

        self.btn_analyze.setEnabled(True)
        self.btn_open.setText("更换文件")

    def start_analysis(self):
        if not self.audio_file:
            return

        self.btn_analyze.setEnabled(False)
        self.progress.setVisible(True)
        self.progress.setValue(0)

        # 后台线程分析
        thread = threading.Thread(target=self.analyzer.analyze, args=(self.audio_file,))
        thread.daemon = True
        thread.start()

    @pyqtSlot(int, str)
    def on_progress(self, value, text):
        self.progress.setValue(value)
        self.progress.setFormat(text)

    @pyqtSlot(dict)
    def on_analysis_complete(self, result):
        self.analysis_result = result
        self.btn_analyze.setEnabled(True)
        self.progress.setVisible(False)

        # 更新UI
        self.lbl_duration.setText(f"时长: {result['duration']:.2f}秒")
        self.lbl_sr.setText(f"采样率: {result['sample_rate']}Hz")
        self.lbl_bpm.setText(f"BPM: {result['fixed_bpm']:.1f}")

        # 更新波形
        self.waveform_widget.set_audio_data(result['waveform'], result['sample_rate'])
        self.waveform_widget.set_beats(result['beat_times'], result['downbeat_times'])

        # 填充表格
        beats = result['beat_times']
        downbeats = set(result['downbeat_times'])

        self.table_beats.setRowCount(0)
        for i, bt in enumerate(beats):
            row = self.table_beats.rowCount()
            self.table_beats.insertRow(row)

            self.table_beats.setItem(row, 0, QTableWidgetItem(str(i+1)))
            self.table_beats.setItem(row, 1, QTableWidgetItem(f"{bt:.4f}"))
            self.table_beats.setItem(row, 2, QTableWidgetItem(str(int(bt*1000))))

            beat_type = "Downbeat" if bt in downbeats else "Beat"
            self.table_beats.setItem(row, 3, QTableWidgetItem(beat_type))

            # downbeat用红色
            if bt in downbeats:
                for col in range(4):
                    item = self.table_beats.item(row, col)
                    item.setBackground(QColor(255, 200, 200))

        # 统计信息
        stats = f"""=== 统计信息 ===
总Beat数: {len(beats)}
Downbeat数: {len(downbeats)}
Onset数: {len(result['onset_times'])}
BPM: {result['fixed_bpm']:.1f}
变BPM点: {len(result['tempo_variations'])}"""

        if result['tempo_variations']:
            stats += "\n\n=== BPM变化 ==="
            for tv in result['tempo_variations']:
                stats += f"\n{tv['time']:.1f}s: {tv['bpm']:.1f} BPM"

        self.stats_text.setText(stats)

        # 启用导出按钮
        self.btn_export_json.setEnabled(True)
        self.btn_export_csv.setEnabled(True)
        self.btn_export_smk.setEnabled(True)

        QMessageBox.information(self, "完成", "分析完成!")

    @pyqtSlot(str)
    def on_analysis_error(self, error):
        self.btn_analyze.setEnabled(True)
        self.progress.setVisible(False)
        QMessageBox.critical(self, "错误", f"分析失败: {error}")

    def export_data(self, fmt):
        if not self.analysis_result:
            return

        file_path = self.analysis_result['file_name']
        base_name = os.path.splitext(file_path)[0]

        if fmt == 'json':
            save_path, _ = QFileDialog.getSaveFileName(
                self, "保存JSON", f"{base_name}_beats.json",
                "JSON文件 (*.json)"
            )
            if save_path:
                # 移除waveform数据
                export_data = {k: v for k, v in self.analysis_result.items() if k != 'waveform'}
                with open(save_path, 'w', encoding='utf-8') as f:
                    json.dump(export_data, f, indent=2, ensure_ascii=False)

        elif fmt == 'csv':
            save_path, _ = QFileDialog.getSaveFileName(
                self, "保存CSV", f"{base_name}_beats.csv",
                "CSV文件 (*.csv)"
            )
            if save_path:
                with open(save_path, 'w', encoding='utf-8') as f:
                    f.write("index,time_ms,time_sec,type\n")
                    beats = self.analysis_result['beat_times']
                    downbeats = set(self.analysis_result['downbeat_times'])
                    for i, bt in enumerate(beats):
                        btype = "downbeat" if bt in downbeats else "beat"
                        f.write(f"{i+1},{int(bt*1000)},{bt:.4f},{btype}\n")

        elif fmt == 'smk':
            # 导出为简化谱面格式 (音游用)
            save_path, _ = QFileDialog.getSaveFileName(
                self, "保存谱面", f"{base_name}.smk",
                "SMK文件 (*.smk)"
            )
            if save_path:
                with open(save_path, 'w', encoding='utf-8') as f:
                    f.write("# Song Chart\n")
                    f.write(f"# File: {self.analysis_result['file_name']}\n")
                    f.write(f"# BPM: {self.analysis_result['fixed_bpm']}\n")
                    f.write(f"# Duration: {self.analysis_result['duration']:.2f}\n")
                    f.write("#\n")

                    beats = self.analysis_result['beat_times']
                    downbeats = set(self.analysis_result['downbeat_times'])

                    for i, bt in enumerate(beats):
                        ms = int(bt * 1000)
                        lane = 0 if bt in downbeats else (i % 4)
                        f.write(f"{ms},{lane},1\n")

        QMessageBox.information(self, "完成", f"已导出为 {fmt.upper()} 格式")


if __name__ == '__main__':
    app = QApplication(sys.argv)
    window = MainWindow()
    window.show()
    sys.exit(app.exec_())