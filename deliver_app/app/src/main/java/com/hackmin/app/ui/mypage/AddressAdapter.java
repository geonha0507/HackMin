package com.hackmin.app.ui.mypage;

// ===== [C] START: 배송지 목록 어댑터 (신규) =====

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hackmin.app.R;
import com.hackmin.app.data.model.user.AddressDto;

import java.util.List;

public class AddressAdapter extends RecyclerView.Adapter<AddressAdapter.AddressViewHolder> {

    public interface OnAddressDeleteListener {
        void onDelete(AddressDto address);
    }

    public interface OnAddressSetDefaultListener {
        void onSetDefault(AddressDto address);
    }

    private final List<AddressDto> addressList;
    private final OnAddressDeleteListener deleteListener;
    private final OnAddressSetDefaultListener setDefaultListener;

    public AddressAdapter(List<AddressDto> addressList, OnAddressDeleteListener deleteListener,
                           OnAddressSetDefaultListener setDefaultListener) {
        this.addressList = addressList;
        this.deleteListener = deleteListener;
        this.setDefaultListener = setDefaultListener;
    }

    @NonNull
    @Override
    public AddressViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_address, parent, false);
        return new AddressViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AddressViewHolder holder, int position) {
        AddressDto item = addressList.get(position);
        holder.tvLabel.setText(item.getLabel() != null ? item.getLabel() : "배송지");
        holder.tvAddress.setText(item.getAddress());
        holder.tvDetail.setText(item.getDetail() != null ? item.getDetail() : "");
        holder.tvDefaultBadge.setVisibility(item.isDefault() ? View.VISIBLE : View.GONE);
        holder.tvSetDefault.setVisibility(item.isDefault() ? View.GONE : View.VISIBLE);

        holder.btnDelete.setOnClickListener(v -> deleteListener.onDelete(item));
        holder.tvSetDefault.setOnClickListener(v -> setDefaultListener.onSetDefault(item));
    }

    @Override
    public int getItemCount() {
        return addressList.size();
    }

    static class AddressViewHolder extends RecyclerView.ViewHolder {
        TextView tvLabel, tvAddress, tvDetail, tvDefaultBadge, tvSetDefault;
        ImageButton btnDelete;

        AddressViewHolder(@NonNull View itemView) {
            super(itemView);
            tvLabel = itemView.findViewById(R.id.tvLabel);
            tvAddress = itemView.findViewById(R.id.tvAddress);
            tvDetail = itemView.findViewById(R.id.tvDetail);
            tvDefaultBadge = itemView.findViewById(R.id.tvDefaultBadge);
            tvSetDefault = itemView.findViewById(R.id.tvSetDefault);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}
// ===== [C] END =====
