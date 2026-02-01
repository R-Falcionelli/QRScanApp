package com.example.qrlookup

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ListeQRAdapter(
    private val items: List<MainActivity.ListeQRRow>,
    private val onRowClick: (MainActivity.ListeQRRow) -> Unit
) : RecyclerView.Adapter<ListeQRAdapter.ViewHolder>() {

    private val fullList = items.toList()           // copie immuable
    private val displayList = items.toMutableList() // ce qu'on affiche
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvAffaire: TextView = itemView.findViewById(R.id.tvAffaire)
        val tvFI: TextView = itemView.findViewById(R.id.tvFi)
        val tvQrId: TextView = itemView.findViewById(R.id.tvQrCode)
        val tvDateSAS: TextView = itemView.findViewById(R.id.tvDate)

        fun bind(item: MainActivity.ListeQRRow) {
            tvQrId.text = item.qrid ?: "-"
            tvAffaire.text = item.affid ?: "-"
            tvFI.text = item.fi ?: "-"
            tvDateSAS.text = item.dateSAS.toString() ?: "-"   // ou formaté

            itemView.setOnClickListener {
                onRowClick(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_row_liste, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = displayList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(displayList[position])
    }

    fun applyFilters(
        qrFilter: String,
        affaireFilter: String,
        fiFilter: String
    ) {
        val qQr = qrFilter.trim()
        val qAff = affaireFilter.trim()
        val qFi = fiFilter.trim()

        displayList.clear()

        displayList.addAll(
            fullList.filter { row ->
                val matchQr =
                    qQr.isEmpty() || row.qrid?.contains(qQr, ignoreCase = true) == true
                val matchAff =
                    qAff.isEmpty() || row.affid?.contains(qAff, ignoreCase = true) == true
                val matchFi =
                    qFi.isEmpty() || row.fi?.contains(qFi, ignoreCase = true) == true

                matchQr && matchAff && matchFi
            }
        )

        notifyDataSetChanged()
    }
}