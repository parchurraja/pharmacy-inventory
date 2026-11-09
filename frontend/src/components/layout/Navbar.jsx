import React, { useEffect, useState } from 'react';
import { Menu, Bell, ShieldCheck, AlertTriangle } from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import { inventoryService } from '../../services/inventoryService';
import { Link, useLocation } from 'react-router-dom';

export function Navbar({ onOpenMobile }) {
  const { user } = useAuth();
  const location = useLocation();
  const [alertCount, setAlertCount] = useState(0);

  useEffect(() => {
    let isMounted = true;
    async function fetchAlertBadges() {
      try {
        const [lowStock, expiring] = await Promise.all([
          inventoryService.getLowStockAlerts(),
          inventoryService.getExpiringBatches(30),
        ]);
        if (isMounted) {
          const total = (lowStock?.length || 0) + (expiring?.length || 0);
          setAlertCount(total);
        }
      } catch {
        // Silently ignore background badge error
      }
    }
    fetchAlertBadges();
    return () => { isMounted = false; };
  }, [location.pathname]);

  const getPageTitle = () => {
    switch (location.pathname) {
      case '/': return 'Operations Dashboard';
      case '/medicines': return 'Medicine Catalog';
      case '/batches': return 'Batch Management & FEFO Tracking';
      case '/sales': return 'Sales & Billing (POS)';
      case '/sales-history': return 'Sales Transactions & Returns';
      case '/alerts': return 'Inventory Alerts & Safety';
      case '/reports': return 'Financial & Stock Reports';
      default: return 'Pharmacy Management';
    }
  };

  return (
    <header className="h-16 bg-white border-b border-slate-200/80 sticky top-0 z-20 flex items-center justify-between px-4 sm:px-6 lg:px-8">
      <div className="flex items-center gap-3">
        <button
          onClick={onOpenMobile}
          className="lg:hidden p-2 rounded-xl text-slate-500 hover:text-slate-700 hover:bg-slate-100 transition-colors"
        >
          <Menu className="w-5 h-5" />
        </button>

        <div>
          <h2 className="text-base sm:text-lg font-bold text-slate-800 tracking-tight leading-none">
            {getPageTitle()}
          </h2>
          <div className="flex items-center gap-1 text-[11px] text-slate-400 mt-1">
            <span>MediStock</span>
            <span>/</span>
            <span className="text-slate-600 font-medium capitalize">
              {location.pathname.substring(1) || 'dashboard'}
            </span>
          </div>
        </div>
      </div>

      <div className="flex items-center gap-3">
        {/* Alerts Shortcut Badge */}
        <Link
          to="/alerts"
          className="relative p-2 rounded-xl text-slate-500 hover:text-teal-700 hover:bg-slate-50 border border-slate-200/70 transition-colors flex items-center gap-2"
          title="Inventory Alerts"
        >
          <Bell className="w-4 h-4" />
          {alertCount > 0 ? (
            <span className="flex items-center gap-1 text-xs font-semibold text-rose-600 bg-rose-50 px-2 py-0.5 rounded-full border border-rose-200">
              <AlertTriangle className="w-3 h-3" />
              {alertCount}
            </span>
          ) : (
            <span className="text-[11px] font-medium text-emerald-600 bg-emerald-50 px-2 py-0.5 rounded-full border border-emerald-200 hidden sm:inline-block">
              All Clear
            </span>
          )}
        </Link>

        {/* User Pill */}
        <div className="flex items-center gap-2 pl-3 border-l border-slate-200/80">
          <div className="w-8 h-8 rounded-full bg-teal-50 border border-teal-200 flex items-center justify-center text-teal-700 font-bold text-xs shrink-0">
            {user?.fullName?.charAt(0) || user?.username?.charAt(0) || 'U'}
          </div>
          <div className="hidden md:block text-left">
            <p className="text-xs font-semibold text-slate-800 leading-tight">{user?.fullName || user?.username}</p>
            <p className="text-[10px] text-teal-700 font-medium flex items-center gap-1">
              <ShieldCheck className="w-3 h-3" />
              {user?.role === 'ROLE_ADMIN' ? 'System Admin' : 'Staff Pharmacist'}
            </p>
          </div>
        </div>
      </div>
    </header>
  );
}
