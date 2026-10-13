export const lookupConfig:Record<string,{title:string;singular:string;codeWidth:number;help:string}>={
  "vehicle-types":{title:"انواع خودرو",singular:"نوع خودرو",codeWidth:2,help:"سطح اول کد خودرو؛ کد رکورد جدید به‌صورت خودکار از ادامه کدهای موجود ساخته می‌شود."},
  "vehicle-brands":{title:"برند خودرو بر اساس نوع",singular:"برند خودرو",codeWidth:2,help:"کد برند داخل هر نوع خودرو مستقل است و برای همان نوع از آخرین کد ادامه پیدا می‌کند."},
  "part-brands":{title:"برندهای قطعه",singular:"برند قطعه",codeWidth:2,help:"سازنده/برند قطعه و اطلاعات کشور طراحی/تولید؛ کد جدید خودکار است."},
  "packaging":{title:"بسته‌بندی",singular:"نوع بسته‌بندی",codeWidth:2,help:"نوع بسته‌بندی استفاده‌شده در کد محصول؛ کد جدید خودکار است."},
  "qualities":{title:"کیفیت",singular:"کیفیت",codeWidth:1,help:"کیفیت یک رقم از کد محصول است و کد جدید از ادامه کدهای موجود تخصیص می‌یابد."},
  "product-groups":{title:"گروه‌های محصول",singular:"گروه محصول",codeWidth:4,help:"طبقه‌بندی داخلی محصولات برای جستجو و گزارش؛ کد جدید خودکار است."},
  "units":{title:"واحدها",singular:"واحد",codeWidth:2,help:"عدد، جفت، دست و سایر واحدهای فروش؛ کد جدید خودکار است."},
  "physical-codes":{title:"کدهای فیزیکی",singular:"مشخصات فیزیکی",codeWidth:4,help:"وزن و ابعاد قابل استفاده مجدد برای محصول؛ کد جدید خودکار است."}
};
export const lookupOrder=["vehicle-types","vehicle-brands","part-brands","packaging","qualities","product-groups","units","physical-codes"];
