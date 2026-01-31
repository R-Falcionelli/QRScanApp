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

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvAffaire: TextView = itemView.findViewById(R.id.tvAffaire)
        val tvFI: TextView = itemView.findViewById(R.id.tvFi)
        val tvQrId: TextView = itemView.findViewById(R.id.tvQrCode)
        val tvDateSAS: TextView = itemView.findViewById(R.id.tvDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_row_liste, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val liste = items[position]

        holder.tvAffaire.text = liste.affid
        holder.tvFI.text = liste.fi
        holder.tvQrId.text = liste.qrid
        holder.tvDateSAS.text = liste.dateSAS.toString() ?: "-"
        holder.itemView.setOnClickListener {onRowClick(liste)}
    }
}