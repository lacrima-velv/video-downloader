package com.example.videodownloader

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class DeniedPermissionDialogExplanation : DialogFragment() {

//    override fun onCreateView(
//        inflater: LayoutInflater, container: ViewGroup?,
//        savedInstanceState: Bundle?
//    ): View? {
//        // Inflate the layout for this fragment
//        return inflater.inflate(R.layout.fragment_denied_permission_dialog_explanation, container, false)
//    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("Permission to storage is not granted")
            .setMessage("Previously you denied permission to storage, so we cannot download a video")
            .setPositiveButton("Ok") {
                    dialog, _ -> dialog.dismiss()
            }
            .show()
    }
}