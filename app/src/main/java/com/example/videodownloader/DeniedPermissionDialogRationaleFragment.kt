package com.example.videodownloader

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class DeniedPermissionDialogRationaleFragment : DialogFragment() {

//    override fun onCreateView(
//        inflater: LayoutInflater, container: ViewGroup?,
//        savedInstanceState: Bundle?
//    ): View? {
//        // Inflate the layout for this fragment
//        return inflater.inflate(R.layout.fragment_denied_permission_dialog_rationale, container, false)
//    }

    private lateinit var listener: DialogClickListener
    /*
    Parent fragment must implement this interface to set click listener on the button to call
    permission request
     */
    interface DialogClickListener {
        fun onYesClick()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            listener = activity as DialogClickListener
        } catch (e: ClassCastException) {
            throw ClassCastException("$context must implement DialogClickListener")
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("Storage permission is required")
            .setMessage("Video can't be downloaded until permission to read external storage is granted")
            .setPositiveButton("Ok") { _, _ ->
                listener.onYesClick()
            }
            .setNegativeButton("No, thanks") {
                    dialog, _ -> dialog.dismiss()
            }
            .show()
    }
}