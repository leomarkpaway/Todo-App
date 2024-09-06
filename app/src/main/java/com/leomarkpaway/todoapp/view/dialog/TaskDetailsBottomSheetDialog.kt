package com.leomarkpaway.todoapp.view.dialog

import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.ShapeAppearanceModel
import com.leomarkpaway.todoapp.R
import com.leomarkpaway.todoapp.MyApp
import com.leomarkpaway.todoapp.common.enum.TaskStatus
import com.leomarkpaway.todoapp.common.extension.setRippleEffect
import com.leomarkpaway.todoapp.common.extension.toLocalDate
import com.leomarkpaway.todoapp.common.extension.toTimeString
import com.leomarkpaway.todoapp.common.util.PopupMenu
import com.leomarkpaway.todoapp.common.util.getDateIndicator
import com.leomarkpaway.todoapp.common.util.viewModelFactory
import com.leomarkpaway.todoapp.data.source.local.entity.Task
import com.leomarkpaway.todoapp.databinding.BottomSheetDialogTaskDetailsBinding
import com.leomarkpaway.todoapp.view.dialog.create_update_delete_task.DeleteTaskDialog
import com.leomarkpaway.todoapp.view.dialog.schedule.SetScheduleBottomSheetDialog
import com.leomarkpaway.todoapp.view.dialog.schedule.viewmodel.ScheduleSharedViewModel
import com.leomarkpaway.todoapp.view.home.HomeFragmentDirections
import com.leomarkpaway.todoapp.view.task_detail.viewmodel.TaskDetailViewModel
import timber.log.Timber
import java.util.Calendar
import java.util.Locale

class TaskDetailsBottomSheetDialog(private val taskId: Long) : BottomSheetDialogFragment() {
    private lateinit var binding: BottomSheetDialogTaskDetailsBinding
    private val viewModel: TaskDetailViewModel by activityViewModels {
        viewModelFactory { TaskDetailViewModel(MyApp.appModule.repository) }
    }
    private val scheduleSharedViewModel: ScheduleSharedViewModel by activityViewModels {
        viewModelFactory { ScheduleSharedViewModel() }
    }
    private var taskDetails: Task? = null
    private var isTaskDone = false
    private var scheduleDialog: SetScheduleBottomSheetDialog? = null
    private var scheduleMillis = 0L
    private var isReminderOn = false
    private var reminder = 0
    private var isPinned = false
    private var popupMenu: PopupMenu? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = BottomSheetDialogTaskDetailsBinding.inflate(inflater, container, false)
        val shapeAppearanceModel = ShapeAppearanceModel.builder()
            .setTopLeftCorner(CornerFamily.ROUNDED, 50f)
            .setTopRightCorner(CornerFamily.ROUNDED, 50f)
            .setBottomLeftCorner(CornerFamily.CUT, 0f)
            .setBottomRightCorner(CornerFamily.CUT, 0f)
            .build()
        binding.cvContainer.shapeAppearanceModel = shapeAppearanceModel
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.getTaskById(taskId)
        onMaskAsDone()
        onClickScheduleDetail()
        onClickBackButton()
        onClickMiniMenu()
        observeCurrentTaskDetails()
        observeIsTaskDone()
        observerScheduleMillis()
        observerIsReminderOn()
        observerReminder()
        observerInPinned()
    }

    private fun onMaskAsDone() = with(binding) {
        cbMarkAsDone.setOnClickListener {
            viewModel.updateIsTaskDone(cbMarkAsDone.isChecked)
        }
    }

    private fun onClickScheduleDetail() {
        binding.conScheduleDetail.setOnClickListener {
            if (taskDetails != null) {
                scheduleDialog =
                    SetScheduleBottomSheetDialog(scheduleMillis, isReminderOn, reminder)
                scheduleDialog?.show(requireActivity().supportFragmentManager, "set_schedule")
            }
        }
    }

    private fun onClickBackButton() {
        binding.imgBack.setOnClickListener {
            dismiss()
        }
    }

    private fun onClickMiniMenu() = with(binding) {
        popupMenu = PopupMenu(requireContext(), imgMiniMenu, R.menu.popup_menu_action)
        popupMenu?.hideMenuItem(R.id.menu_update)
        imgMiniMenu.setRippleEffect()
        imgMiniMenu.setOnClickListener {
            popupMenu?.setOnMenuItemClickListener { menu ->
                when (menu.itemId) {
                    R.id.menu_show_details -> {
                        Timber.d("menu_show_details")
                        val action = HomeFragmentDirections.actionHomeFragmentToTaskDetailFragment(taskId)
                        findNavController().navigate(action)
                        dismiss()
                    }
                    R.id.menu_pin -> viewModel.updateIsPinned(!isPinned)
                    R.id.menu_delete -> {
                        taskDetails?.let { task ->
                            DeleteTaskDialog(task) { dismiss() }.show(
                                requireActivity().supportFragmentManager,
                                "delete_task"
                            )
                        }
                    }
                }
            }
            popupMenu?.show()
        }
    }

    private fun observeCurrentTaskDetails() {
        viewModel.currentTaskDetails.observe(viewLifecycleOwner) { task ->
            taskDetails = task
            setupTaskDetails(task)
            viewModel.updateIsTaskDone(task.status == TaskStatus.DONE.name)
            viewModel.updateIsPinned(task.isPinned)
            scheduleSharedViewModel.updateSelectDateMillis(task.scheduleMillis)
            scheduleSharedViewModel.updateIsReminderOn(task.isReminderOn)
            scheduleSharedViewModel.updateSelectReminder(task.reminder)
        }
    }

    private fun observeIsTaskDone() {
        viewModel.isTaskDone.observe(viewLifecycleOwner) { isDone ->
            isTaskDone = isDone
            binding.cbMarkAsDone.isChecked = isDone
            if (isTaskDone) {
                completedTaskUiState()
            } else {
                pendingTaskUiState()
            }
        }
    }

    private fun observerScheduleMillis() {
        scheduleSharedViewModel.selectDateMillis.observe(viewLifecycleOwner) {
            scheduleMillis = it
            setScheduleDetail(scheduleMillis, isReminderOn, reminder)
        }
    }

    private fun observerIsReminderOn() {
        scheduleSharedViewModel.isReminderOn.observe(viewLifecycleOwner) {
            isReminderOn = it
            setScheduleDetail(scheduleMillis, isReminderOn, reminder)
        }
    }

    private fun observerReminder() {
        scheduleSharedViewModel.selectedReminder.observe(viewLifecycleOwner) {
            reminder = it
            setScheduleDetail(scheduleMillis, isReminderOn, reminder)
        }
    }

    private fun observerInPinned() {
        viewModel.isPinned.observe(viewLifecycleOwner) { isPinned ->
            this.isPinned = isPinned
            popupMenu?.setMenuTitle(R.id.menu_pin, if (isPinned) "Unpin Task" else "Pin Task")
            popupMenu?.setMenuIcon(
                R.id.menu_pin,
                if (isPinned) R.drawable.ic_pin else R.drawable.ic_unpin
            )
        }
    }

    private fun setupTaskDetails(task: Task) = with(binding) {
        setScheduleDetail(task.scheduleMillis, task.isReminderOn, task.reminder)
        edtTitle.setText(task.title.capitalize(Locale.getDefault()))
        edtTitle.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                edtDescription.requestFocus()
                true
            } else {
                false
            }
        }
        edtDescription.setText(task.description)

        when (task.status) {
            TaskStatus.PENDING.name -> {
                pendingTaskUiState()
            }

            TaskStatus.DONE.name -> {
                completedTaskUiState()
            }

            TaskStatus.EXPIRED.name -> {
                expiredTaskUiState()
            }
        }
    }

    private fun pendingTaskUiState() = with(binding) {
        cbMarkAsDone.isChecked = false
        edtTitle.paintFlags = edtTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        tvSchedule.setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
        tvReminder.setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
    }

    private fun completedTaskUiState() = with(binding) {
        cbMarkAsDone.isChecked = true
        edtTitle.paintFlags = edtTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        tvSchedule.setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
        tvReminder.setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
    }

    private fun expiredTaskUiState() = with(binding) {
        cbMarkAsDone.isChecked = false
        edtTitle.paintFlags = edtTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        tvSchedule.setTextColor(ContextCompat.getColor(requireContext(), R.color.red))
        tvReminder.setTextColor(ContextCompat.getColor(requireContext(), R.color.red))
    }

    private fun setScheduleDetail(dateTime: Long, isReminderOn: Boolean, reminder: Int) =
        with(binding) {
            val calendar = Calendar.getInstance()
            val time = if (isReminderOn) dateTime.toTimeString(calendar)
                .lowercase(Locale.getDefault()) else ""
            val date = getDateIndicator(dateTime.toLocalDate())
            val reminderOption = requireContext().resources.getStringArray(R.array.reminder_options)
            val reminder = if (isReminderOn) reminderOption[reminder] else "none"

            tvSchedule.text = getString(R.string.date_schedule_detail, date, time)
            tvReminder.text = getString(R.string.reminder_detail, reminder)
        }

    private fun submitChanges() = with(binding) {
        if (taskDetails != null) {
            val title = edtTitle.text.toString()
            val description = edtDescription.text.toString()
            val status = if (isTaskDone)  TaskStatus.DONE.name else TaskStatus.PENDING.name
            val updateTask = taskDetails?.copy(
                title = title,
                description = description,
                isReminderOn = isReminderOn,
                reminder = reminder,
                status = status,
                scheduleMillis = scheduleMillis,
                isPinned = isPinned
            )
            viewModel.submitTaskDetailsChanges(updateTask!!)
        }
    }

    override fun onPause() {
        super.onPause()
        submitChanges()
        scheduleDialog?.resetDialogState()
    }

}
