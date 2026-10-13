export type Role = "SUPER_ADMIN" | "MANAGER" | "ADMIN" | "PRODUCT_ADMIN" | "PROCUREMENT" | "FINANCE" | "SHIPPING" | "SUPPORT" | "CUSTOMER";
export interface AuthMe { id:string; username:string; email?:string|null; phone:string; displayName:string; roles:Role[]; managerLevel?:number|null; root:boolean }
export interface PanelSummary { user:AuthMe; metrics:Record<string,number> }
export interface LookupAdminRow { id:string; code:number; descriptionFa:string; descriptionEn:string; active:boolean; vehicleTypeCodeId?:string|null; designCountry?:string|null; manufacturingCountry?:string|null; weightKg?:number|null; lengthCm?:number|null; widthCm?:number|null; heightCm?:number|null }
export interface WorkflowTag { id:string; slug:string; labelFa:string; colorKey:string; source:string }
export interface WorkflowCase { id:string; caseNo:number; requestNo:number; source:string; contactName?:string|null; contactPhone?:string|null; requestedPartNumber?:string|null; requestText:string; vehicleText?:string|null; productId?:string|null; stage:string; priority:string; agreementStatus:string; nextActionAt?:string|null; notes?:string|null; updatedAt:string; tags:WorkflowTag[] }
export interface WorkflowOverview { openCases:number; newRequests:number; sourcing:number; waitingCustomer:number; shipping:number; customs:number; delivered:number; unreadContacts:number }
export interface NewsPost { id:string; slug:string; title_fa?:string; titleFa?:string; summary_fa?:string|null; summaryFa?:string|null; body_fa?:string; bodyFa?:string; published_at?:string|null; publishedAt?:string|null }
export interface NotificationRow { id:string; audience:string; channel:string; status:string; kind:string; title:string; body:string; link?:string|null; caseId?:string|null; productId?:string|null; readAt?:string|null; createdAt:string }
export interface ProductPrice { id:string; productId:string; amount:number; currency:string; validFrom:string; validUntil:string; status:string; note?:string|null; createdBy?:string|null; createdAt:string }
export interface ProductPriceState { state:"VALID"|"MISSING"|"EXPIRED"; current?:ProductPrice|null; latest?:ProductPrice|null }
