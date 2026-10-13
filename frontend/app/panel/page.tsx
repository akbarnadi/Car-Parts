"use client";

import Link from "next/link";
import {useEffect,useState} from "react";
import {useRouter} from "next/navigation";
import {api} from "@/lib/api";
import {PanelSummary,Role} from "@/types/platform";

const roleName:Record<Role,string>={SUPER_ADMIN:"مدیر ریشه",MANAGER:"مدیر",ADMIN:"ادمین",PRODUCT_ADMIN:"مدیر محصول",PROCUREMENT:"تدارکات",FINANCE:"مالی",SHIPPING:"حمل و ترخیص",SUPPORT:"پشتیبانی",CUSTOMER:"مشتری"};

type NavItem={href:string;label:string;hint:string};

export default function Panel(){
  const router=useRouter();
  const [data,setData]=useState<PanelSummary|null>(null),[error,setError]=useState("");
  useEffect(()=>{api.panelSummary().then(setData).catch(()=>setError("برای مشاهده پنل ابتدا وارد شوید."))},[]);
  async function logout(){await api.logout();router.push("/");router.refresh()}
  if(!data)return <div className="pageShell">{error?<div className="errorBanner">{error} <Link href="/login">ورود</Link></div>:<div className="loadingCard">در حال بارگذاری پنل…</div>}</div>;

  const roles=data.user.roles;
  const manager=roles.some(r=>["SUPER_ADMIN","MANAGER"].includes(r));
  const admin=roles.some(r=>["SUPER_ADMIN","MANAGER","ADMIN"].includes(r));
  const operational=roles.some(r=>["SUPER_ADMIN","MANAGER","PROCUREMENT","FINANCE","SHIPPING","SUPPORT"].includes(r));
  const product=roles.some(r=>["SUPER_ADMIN","MANAGER","ADMIN","PRODUCT_ADMIN"].includes(r));
  const support=roles.some(r=>["SUPER_ADMIN","MANAGER","ADMIN","SUPPORT"].includes(r));
  const procurement=roles.some(r=>["SUPER_ADMIN","MANAGER","PROCUREMENT"].includes(r));

  const navigation:NavItem[]=[
    {href:"/panel/notifications",label:"اعلان‌ها",hint:"تغییرات و موارد نیازمند اقدام"},
    ...(roles.includes("CUSTOMER")?[{href:"/panel/requests",label:"درخواست‌های من",hint:"پیگیری درخواست‌های تامین"},{href:"/panel/orders",label:"سفارش‌های من",hint:"جزئیات پرداخت و حمل"}]:[]),
    ...(operational?[{href:"/manager",label:"مرکز عملیات",hint:"درخواست تا تحویل"}]:[]),
    ...(product?[{href:"/admin/product-requests",label:"شناسایی محصول",hint:"اتصال درخواست به محصول"},{href:"/admin/products",label:"محصولات",hint:"کاتالوگ و تاریخچه"},{href:"/admin/codes",label:"جداول کد",hint:"نوع خودرو، برند و کدها"}]:[]),
    ...(procurement?[{href:"/admin/suppliers",label:"تامین‌کنندگان",hint:"دفتر تامین خارجی"}]:[]),
    ...(support?[{href:"/admin/inbox",label:"صندوق پیام",hint:"پیام‌های سایت و پشتیبانی"},{href:"/admin/news",label:"اخبار",hint:"محتوای عمومی"}]:[]),
    ...(manager?[{href:"/admin/users",label:"کاربران و دسترسی",hint:"مشتری، ادمین و مدیر"},{href:"/admin/tags",label:"برچسب‌ها",hint:"تعریف برچسب‌های دستی"}]:[]),
    ...(admin?[{href:"/admin/operations",label:"عملیات ادمین",hint:"صف درخواست و برچسب‌ها"}]:[])
  ];

  return <div className="pageShell workspaceShell"><div className="workspaceLayout">
    <aside className="workspaceSidebar">
      <div className="workspaceIdentity"><span className="workspaceAvatar">{data.user.displayName.slice(0,1)}</span><div><b>{data.user.displayName}</b><small dir="ltr">@{data.user.username}</small></div></div>
      <div className="workspaceRoles">{roles.map(r=><span key={r}>{roleName[r]}</span>)}</div>
      <nav className="workspaceNav">{navigation.map(n=><Link href={n.href} key={n.href}><b>{n.label}</b><small>{n.hint}</small></Link>)}</nav>
      <div className="workspaceSidebarFoot"><Link href="/order">+ درخواست جدید</Link><button onClick={logout}>خروج از حساب</button></div>
    </aside>

    <section className="workspaceMain">
      <header className="workspaceHeader"><div><span className="eyebrow">Workspace</span><h1>سلام، {data.user.displayName}</h1><p>موارد مهم و مسیرهای کاری شما در یک صفحه ساده.</p></div>{data.user.root&&<span className="rootBadge">مدیر صفر</span>}</header>
      <div className="workspaceMetrics">{Object.entries(data.metrics).map(([k,v])=><div key={k}><b>{v.toLocaleString("fa-IR")}</b><span>{k}</span></div>)}</div>

      <div className="workspaceSection"><div className="workspaceSectionHead"><h2>میانبرهای کاری</h2><span>{navigation.length} بخش</span></div><div className="workspaceList">{navigation.map((n,i)=><Link href={n.href} key={n.href} className="workspaceListRow"><span className="workspaceIndex">{String(i+1).padStart(2,"0")}</span><div><b>{n.label}</b><small>{n.hint}</small></div><span className="workspaceArrow">←</span></Link>)}</div></div>

      <div className="workspaceSection"><div className="workspaceSectionHead"><h2>اقدام سریع</h2></div><div className="quickActions"><Link href="/order">ثبت درخواست قطعه</Link>{product&&<Link href="/admin/products/new">محصول جدید</Link>}{operational&&<Link href="/manager">باز کردن مرکز عملیات</Link>}{manager&&<Link href="/admin/users">مدیریت کاربران</Link>}</div></div>
    </section>
  </div></div>;
}
