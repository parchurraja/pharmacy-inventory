import React from 'react';

export function StatusBadge({ status, label, size = 'md' }) {
  const normStatus = (status || '').toString().toUpperCase();

  const getStyle = () => {
    switch (normStatus) {
      case 'ACTIVE':
      case 'COMPLETED':
      case 'SUCCESS':
        return 'bg-emerald-50 text-emerald-700 border-emerald-200/80';
      case 'INACTIVE':
        return 'bg-slate-100 text-slate-600 border-slate-200';
      case 'LOW_STOCK':
      case 'WARNING':
      case 'EXPIRING_SOON':
      case 'PARTIALLY_REFUNDED':
        return 'bg-amber-50 text-amber-700 border-amber-200/80';
      case 'EXPIRED':
      case 'CRITICAL':
      case 'OUT_OF_STOCK':
      case 'DAMAGE':
        return 'bg-rose-50 text-rose-700 border-rose-200/80';
      case 'REFUNDED':
        return 'bg-purple-50 text-purple-700 border-purple-200/80';
      case 'RETURN':
        return 'bg-blue-50 text-blue-700 border-blue-200/80';
      case 'EXPIRED_DISPOSAL':
        return 'bg-stone-100 text-stone-700 border-stone-200';
      default:
        return 'bg-teal-50 text-teal-700 border-teal-200/80';
    }
  };

  const getDisplayLabel = () => {
    if (label) return label;
    switch (normStatus) {
      case 'ACTIVE': return 'Active';
      case 'INACTIVE': return 'Inactive';
      case 'COMPLETED': return 'Completed';
      case 'PARTIALLY_REFUNDED': return 'Partially Refunded';
      case 'REFUNDED': return 'Refunded';
      case 'LOW_STOCK': return 'Low Stock';
      case 'CRITICAL': return 'Critical';
      case 'EXPIRED': return 'Expired';
      case 'DAMAGE': return 'Damaged';
      case 'RETURN': return 'Returned';
      case 'EXPIRED_DISPOSAL': return 'Disposed';
      default: return status || 'Unknown';
    }
  };

  const sizeStyles = size === 'sm' ? 'px-2 py-0.5 text-xs' : 'px-2.5 py-1 text-xs';

  return (
    <span className={`inline-flex items-center font-medium rounded-full border ${getStyle()} ${sizeStyles}`}>
      <span className="w-1.5 h-1.5 rounded-full bg-current mr-1.5 opacity-70" />
      {getDisplayLabel()}
    </span>
  );
}
