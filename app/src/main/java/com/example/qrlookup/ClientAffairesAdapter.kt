package com.example.qrlookup

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ClientAffairesAdapter(
    private val items: List<MainActivity.ClientAffaire>,
    private val onItemClick: (MainActivity.ClientAffaire) -> Unit
) : RecyclerView.Adapter<ClientAffairesAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvAffaire: TextView = itemView.findViewById(R.id.tvAffaire)
        val tvFI: TextView = itemView.findViewById(R.id.tvFI)
        val tvDesignation: TextView = itemView.findViewById(R.id.tvDesignation)
        val tvMarqueType: TextView = itemView.findViewById(R.id.tvMarqueType)
        val tvSerieInterne: TextView = itemView.findViewById(R.id.tvSerieInterne)
        val tvBlDate: TextView = itemView.findViewById(R.id.tvBlDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_affaire_client, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val affaire = items[position]

        holder.tvAffaire.text =
            "Affaire : ${affaire.affId}"

        holder.tvFI.text =
            "N°FI : ${affaire.affNoFI}"

        holder.tvDesignation.text =
            affaire.designation ?: "Sans désignation"

        holder.tvMarqueType.text =
            "Marque : ${affaire.marque ?: "-"} | Type : ${affaire.type ?: "-"}"

        holder.tvSerieInterne.text =
            "N°Série : ${affaire.serie} | N°Interne : ${affaire.interne ?: "-"}"

        holder.tvBlDate.text =
            "BL : ${affaire.bl ?: "-"}  |  Date : ${affaire.blDate ?: "-"}"

        holder.itemView.setOnClickListener {
            onItemClick(affaire)
        }
    }
}