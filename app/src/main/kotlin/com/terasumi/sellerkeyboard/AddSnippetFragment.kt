package com.terasumi.sellerkeyboard

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.material.snackbar.Snackbar
import com.terasumi.sellerkeyboard.databinding.AddSnippetFragmentBinding
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Objects
import java.util.UUID

class AddSnippetFragment : Fragment() {
    private var binding: AddSnippetFragmentBinding? = null
    private var imagePickerLauncher: ActivityResultLauncher<Intent>? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return try {
            binding = AddSnippetFragmentBinding.inflate(inflater, container, false)
            binding!!.root
        } catch (e: Exception) {
            Log.e("AddSnippetFragment", "Error in onCreateView", e)
            null
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            imagePickerLauncher = registerForActivityResult<Intent, ActivityResult>(
                ActivityResultContracts.StartActivityForResult()
            ) { result: ActivityResult ->
                try {
                    if (result.resultCode == Activity.RESULT_OK) {
                        val data = result.data
                        if (data != null && data.data != null) {
                            val imageUri = data.data
                            val imageView =
                                binding!!.root.findViewById<ImageView>(R.id.imagePickerView)
                            imageView.setImageURI(imageUri)
                            imageView.tag = imageUri
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AddSnippetFragment", "Error handling activity result", e)
                }
            }

            binding!!.imagePickerView.setOnClickListener { v: View? ->
                try {
                    openImagePicker()
                } catch (e: Exception) {
                    Log.e("AddSnippetFragment", "Error opening image picker", e)
                }
            }

            binding!!.submitButton.setOnClickListener { v: View? ->
                try {
                    if (validateFormData()) {
                        uploadImageAndSaveData()
                    }
                } catch (e: Exception) {
                    Log.e("AddSnippetFragment", "Error submitting data", e)
                }
            }
        } catch (e: Exception) {
            Log.e("AddSnippetFragment", "Error in onViewCreated", e)
        }
    }

    private fun validateFormData(): Boolean {
        return try {
            // Add validation checks for title and description if needed
            true
        } catch (e: Exception) {
            Log.e("AddSnippetFragment", "Error validating form data", e)
            false
        }
    }

    private fun uploadImageAndSaveData() {
        try {
            val title = binding!!.titleEditText.text.toString()
            Log.d("Title", title)
            val description = binding!!.contentEditText.text.toString()
            Log.d("Description", description)
            if (binding!!.imagePickerView.tag == null) {
//                Snackbar.make(binding!!.root, "Vui lòng chọn ảnh", Snackbar.LENGTH_SHORT).show()
//                return
                saveDataToSQLite(title, description, null)
                Log.d("SQLite", "Snippet added to SQLite database, no image");
            } else {
                saveImageToInternalStorage(binding!!.root)
            }
        } catch (e: Exception) {
            Log.e("AddSnippetFragment", "Error uploading image and saving data", e)
        }
    }

    private fun saveImageToInternalStorage(view: View) {
        try {
            val imageUri = binding!!.imagePickerView.tag as Uri
            Glide.with(this)
                .asBitmap()
                .load(imageUri)
                .into(
                    object : CustomTarget<Bitmap>() {
                        override fun onResourceReady(
                            resource: Bitmap,
                            transition: Transition<in Bitmap>?
                        ) {
                            try {
                                val imageUrl = saveImageWithGlide(resource)
                                saveDataToSQLite(
                                    Objects.requireNonNull(binding!!.titleEditText.text).toString(),
                                    Objects.requireNonNull(
                                        binding!!.contentEditText.text
                                    ).toString(),
                                    imageUrl
                                )
                            } catch (e: Exception) {
                                Log.e("AddSnippetFragment", "Error saving image with Glide", e)
                            }
                        }

                        override fun onLoadCleared(placeholder: Drawable?) {
                            // Handle placeholder if needed
                        }
                    }
                )
            Snackbar.make(binding!!.root, "Lưu ảnh thành công!", Snackbar.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("AddSnippetFragment", "Error saving image to internal storage", e)
        }
    }

    private fun saveImageWithGlide(bitmap: Bitmap): String {
        return try {
            val imageName = "img_" + UUID.randomUUID().toString() + ".jpg"
            val file = File(requireContext().cacheDir, imageName) // Or another suitable directory
            try {
                FileOutputStream(file).use { fos ->
                    bitmap.compress(
                        Bitmap.CompressFormat.JPEG,
                        100,
                        fos
                    ) // You can adjust the quality (0-100)
                }
            } catch (e: IOException) {
                e.printStackTrace() // Handle the exception appropriately
            }
            // Now you have the file path: file.getAbsolutePath()
            Log.d("Image", "Image saved to internal storage: " + file.absolutePath)
            file.absolutePath
        } catch (e: Exception) {
            Log.e("AddSnippetFragment", "Error saving image with Glide", e)
            ""
        }
    }

    private fun saveDataToSQLite(title: String, content: String, imageUrls: String?) {
        try {
            // Insert a new snippet
            //if ImageUrls is null, it means no image is selected
            val snippet = if (imageUrls != null) {
                Snippet(title, content, imageUrls)
            } else {
                Snippet(title, content, "")
            }
            context?.let { SnippetDbHelper(it) }?.addSnippet(snippet)
            Log.d("SQLite", "Snippet added to SQLite database")

            // Snack bar notification success for user
            Snackbar.make(binding!!.root, "Data saved successfully", Snackbar.LENGTH_SHORT).show()
            // Return to FirstFragment
            parentFragmentManager.popBackStack()
        } catch (e: Exception) {
            Log.e("AddSnippetFragment", "Error saving data to SQLite", e)
        }
    }

    private fun openImagePicker() {
        try {
            val intent = Intent()
            intent.setType("image/*")
            intent.setAction(Intent.ACTION_GET_CONTENT)
            imagePickerLauncher!!.launch(intent)
        } catch (e: Exception) {
            Log.e("AddSnippetFragment", "Error opening image picker", e)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}