import type { Metadata } from "next";
import Link from "next/link";
import SiteAuth from "@/components/SiteAuth";
import "./globals.css";
import "./liquid-glass.css";
import "./mobile-dock.css";
import "./pricing-workbench.css";
import "./modern-workspace.css";

export const metadata:Metadata = { title:"قطعه‌یار | تامین و مدیریت قطعات خودرو", description:"جستجو، درخواست، تامین و پیگیری قطعات خودرو" };

export default function RootLayout({children}:{children:React.ReactNode}) {
  return <html lang="fa" dir="rtl"><body>
    <header className="siteHeader"><div className="navShell">
      <Link href="/" className="logo"><span className="logoMark">ق</span><span><b>قطعه‌یار</b><small>تامین هوشمند قطعات</small></span></Link>
      <nav className="mainNav" aria-label="ناوبری اصلی"><Link href="/products">محصولات</Link><Link href="/order">درخواست قطعه</Link><Link href="/news">اخبار</Link><Link href="/contact">تماس با ما</Link></nav>
      <SiteAuth/>
    </div></header>
    <main>{children}</main>
    <nav className="mobileDock" aria-label="ناوبری موبایل"><Link href="/">خانه</Link><Link href="/products">محصولات</Link><Link href="/order">درخواست</Link><Link href="/panel">پنل</Link></nav>
    <footer className="siteFooter"><div><b>قطعه‌یار</b><p>مدیریت درخواست، تامین خارجی، قیمت‌گذاری و تحویل قطعات در یک مسیر شفاف.</p></div><div><Link href="/order">ثبت درخواست</Link><Link href="/products">محصولات</Link><Link href="/contact">پشتیبانی</Link></div><small>© ۲۰۲۶ — سامانه مدیریت قطعات خودرو</small></footer>
  </body></html>;
}
