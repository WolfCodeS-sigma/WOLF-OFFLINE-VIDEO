
# WOLF OFFLINE VIDEO — تدقيق الفروع وطلبات الدمج وخطة استعادة التكامل

**تاريخ لقطة التدقيق:** 23 نوفمبر 2026  
**المستودع:** WolfCodeS-sigma/WOLF-OFFLINE-VIDEO  
**نوع المستند:** تقرير تدقيق بنيوي وخريطة استعادة. لا ينفذ أي دمج أو تعديل لقواعد الفروع.

## 1. هدف التدقيق وحدوده

أُعد هذا التقرير لتحديد كيفية إدخال الميزات الفريدة الموجودة في الفروع القديمة إلى خط تطوير واحد، دون إسقاط وظائف أو تكرار تغييرات أو استبدال مسار إنتاج بآخر دون مراجعة.

شمل التدقيق:
- جرد الفروع المتاحة في المستودع.
- مراجعة بيانات طلبات الدمج PR #1 إلى PR #34، بما فيها Base وHead وCommit SHA والحالة والوصف.
- استخراج قائمة الملفات المتغيرة الكاملة لكل طلب.
- مقارنة علاقات Commit ancestry وMerge Base بين الطلبات المتداخلة والمتباعدة.
- مراجعة التغييرات التفصيلية في مجموعة من الملفات المشتركة عالية الخطورة، خصوصًا OfflineProductionEngine.kt وWolfImageDepthSegmenter.kt وملفات Workflow وGradle.
- مطابقة نتائج GitHub Actions مع Commit SHA الذي اختُبر فعلًا.
- إعداد مسار استعادة مقترح يحافظ على التغييرات الفريدة ويمنع الدمج المكرر.

**حدود التقرير:** تمت مراجعة بيانات ونطاق وقوائم ملفات كل PR، كما تمت مراجعة التغييرات التفصيلية لمجموعة الملفات الأساسية المتداخلة. لم تُجرَ بعد مراجعة سطرية كاملة لكل ملف فريد من جميع الطلبات الـ34؛ لذلك لا يمثل هذا التقرير إذنًا بالدمج. يجب إكمال مراجعة الكود الكامل للملفات التي ستدخل كل PR قبل تنفيذ الدمج الفعلي.

## 2. النتيجة التنفيذية

- عدد الفروع في لقطة GitHub: **40 فرعًا**.
- عدد Pull Requests: **34 طلبًا**.
- الحالة وقت التدقيق: **34 مفتوحًا وDraft، وصفر مدمج**.
- لم تدخل تغييرات هذه الطلبات إلى main بواسطة Merge حتى وقت اللقطة.
- توجد عائلتا تطوير رئيسيتان انطلقتا من main نفسه ثم تطورتا بشكل مستقل:
  1. مسار Phase PR #1–#18.
  2. مسار Stage PR #21–#34.
- هذان المساران **متباعدان تاريخيًا**، وليست إحداهما فرعًا ابنًا للأخرى. وقد عدّلا 12 ملفًا مركزيًا مشتركًا على الأقل.
- لذلك لا يجوز دمج المسارين كاملين أحدهما بعد الآخر دون توحيد دلالي للتغييرات المتعارضة في ملفات الإنتاج والواجهة والبناء والاختبارات.
- توجد أيضًا أخطاء تنظيمية داخل كل مسار: قواعد PR قديمة لا تطابق الأب الحقيقي للرأس، وطلبات تراكمية تعرض تغييرات مراحل سابقة مرة أخرى.

**قرار هذه الخطوة:** الاحتفاظ بجميع الفروع وطلبات الدمج وعدم دمج أو حذف أو إعادة تسمية أو إعادة كتابة أي تاريخ. هذه الخطوة تنتهي بخريطة الاستعادة فقط.

## 3. جرد الفروع الأربعين

1. baseline/frozen-2026-11-20-experimental
2. baseline/frozen-2026-11-20
3. design/stage-09-procedural-generation-library
4. design/stage-12-final-engine-roles
5. design/stage-13-motion-code-library-scene-contract
6. development/extension-track
7. development/extension-track-r1
8. docs/offline-roadmap-expansion
9. docs/stage-06-asset-quality-licensing-policy
10. docs/stage-07-cinematic-asset-inventory
11. docs/wolf-motion-ecosystem-plan
12. docs/wolf-offline-video-master-work-plan
13. feature/dfine-instance-segmentation
14. feature/failure-tolerant-image-motion
15. feature/filament-offline-video-prototype
16. feature/phase-7-image-segmentation
17. feature/phase-8-wolf-25d-cinematic-image-engine
18. feature/phase-9-cinematic-motion
19. feature/phase-10-horror-knowledge-library
20. feature/phase-11-wolf-offline-story-ai
21. feature/phase-12-story-length-structure
22. feature/phase-13-story-originality-reference-review
23. feature/phase-14-film-production-engine
24. feature/phase-15-audio-cinematic-identity
25. feature/phase-16-offline-runtime-closure
26. feature/procedural-scene-production-core
27. feature/scene-layers-depth-motion
28. feature/stage-05-storyboard-planner
29. feature/stage-08-asset-registry-quality-gate
30. feature/stage-10-generator-asset-cooperation
31. feature/stage-11-engine-compatibility-poc
32. feature/unified-scene-render-fallback
33. feature/uploaded-image-engine-isolated
34. fix/stage-03-film-duration-timing
35. fix/stage-04-render-fallback
36. fix/stages-01-06-audit
37. fix/stages-01-07-audit
38. main
39. release/phase-18-final-apk-preparation
40. test/phase-17-android-runtime

### 3.1 الفروع الستة التي لا تظهر كرؤوس لطلبات الدمج

| الفرع | المقارنة مع main | التفسير |
|---|---|---|
| baseline/frozen-2026-11-20 | مطابق | نسخة مجمدة مطابقة لـmain |
| development/extension-track | مطابق | فرع دعم مطابق لـmain |
| feature/dfine-instance-segmentation | مطابق | لا توجد تغييرات مستقلة في لقطة المقارنة |
| development/extension-track-r1 | متقدم 4 Commits | نقطة الأساس التجريبية التي تساوي رأس PR #1 |
| baseline/frozen-2026-11-20-experimental | متقدم 4 Commits | نسخة تجريبية مكررة من نقطة PR #1 |
| main | المرجع | خط الأساس الحالي |

تطابق development/extension-track-r1 وbaseline/frozen-2026-11-20-experimental مع Commit PR #1 يعني أنهما ليسا مرحلتين إضافيتين مستقلتين. لا ينبغي دمجهما مرة أخرى بعد إدخال PR #1.

## 4. سجل PR #1 إلى PR #34

الأعداد في عمود الملفات تمثل عدد مسارات الملفات الظاهرة في مقارنة Base المعلنة مع Head. قد يتضمن الرقم ملفات موروثة من مرحلة سابقة إذا كانت قاعدة PR قديمة؛ لذلك لا يُعامل عدد الملفات وحده على أنه عدد التغييرات الفريدة في المرحلة.

| PR | Base → Head | Head SHA المختصر | الملفات في المقارنة المعلنة | النطاق |
|---|---|---|---:|---|
| 1 | main → feature/failure-tolerant-image-motion | d99edcd0 | 4 | جعل فشل فصل الصورة غير قاتل، والرجوع إلى الصورة الأصلية مع استمرار الإنتاج |
| 2 | extension-track-r1 → feature/procedural-scene-production-core | 31e0b64d | 8 | محرك المشاهد الإجرائية لمسار بلا صور مرجعية |
| 3 | extension-track-r1 → feature/scene-layers-depth-motion | fcd6d569 | 13 | طبقات عناصر المشهد والعمق والحركة |
| 4 | scene-layers-depth-motion → uploaded-image-engine-isolated | 75bf899e | 5 | عزل معالجة صور المستخدم |
| 5 | uploaded-image-engine-isolated → unified-scene-render-fallback | e1dc2e85 | 5 | مسار موحد لتوجيه الرندر والبدائل |
| 6 | unified-scene-render-fallback → docs/offline-roadmap-expansion | 8249c98b | 2 | توسيع خارطة الطريق؛ وثائق فقط |
| 7 | unified-scene-render-fallback → phase-7-image-segmentation | e1d74363 | 4 | تحسين التقسيم اللوني/الحدودي للصور؛ لا يمثل تقسيمًا دلاليًا عامًا |
| 8 | phase-7-image-segmentation → phase-8-wolf-25d-cinematic-image-engine | 32cbca7f | 4 | محرك 2.5D لصور المستخدم |
| 9 | phase-8-wolf-25d-cinematic-image-engine → phase-9-cinematic-motion | 428698f6 | 4 | حركة سينمائية للشخصيات/العناصر في مسار الصور |
| 10 | phase-9-cinematic-motion → phase-10-horror-knowledge-library | f2fd7b4f | 7 | مكتبة قصص TXT محلية وخاصة |
| 11 | phase-10-horror-knowledge-library → phase-11-wolf-offline-story-ai | b2318fa5 | 9 | استوديو كتابة القصص المحلية عبر LiteRT-LM |
| 12 | phase-11-wolf-offline-story-ai → phase-12-story-length-structure | 48433370 | 6 | محرك طول القصة والبنية السردية |
| 13 | phase-12-story-length-structure → phase-13-story-originality-reference-review | 228cbe90 | 7 | مراجعة الأصالة والمراجع وأنماط السرد |
| 14 | Base قديم من Phase 13 → phase-14-film-production-engine | c7d58027 | 8 | منسق إنتاج الفيلم والفصول واللقطات والاستئناف |
| 15 | phase-14-film-production-engine → phase-15-audio-cinematic-identity | da580e35 | 14 | تسجيل الراوي المقسم، الصوت، الهوية والمقدمة |
| 16 | phase-15-audio-cinematic-identity → phase-16-offline-runtime-closure | 2094f6c7 | 4 | فحص عقد التشغيل Offline |
| 17 | phase-16-offline-runtime-closure → test/phase-17-android-runtime | 6151f03e | 4 | اختبار تشغيل Android دون اتصال على محاكي |
| 18 | test/phase-17-android-runtime → release/phase-18-final-apk-preparation | 9d5a533e | 2 | بوابة تحضير Release وتوثيقها؛ لا يتضمن APK نهائيًا موقّعًا |
| 19 | release/phase-18-final-apk-preparation → docs/wolf-motion-ecosystem-plan | d6ffca07 | 1 | وثيقة معمارية للمحركات |
| 20 | wolf-motion-ecosystem-plan → wolf-offline-video-master-work-plan | 5c200a35 | 2 | الخطة الهندسية الرئيسية؛ تم تحديثها في خطوة سابقة |
| 21 | main → fix/stage-03-film-duration-timing | 17a60591 | 5 | فصل مدة الفيلم عن توقيت التعليق الصوتي |
| 22 | stage-03-film-duration-timing → fix/stage-04-render-fallback | 040f88aa | 5 | إصلاح الرندر والـFallback وحماية صورة المستخدم |
| 23 | stage-04-render-fallback → stage-05-storyboard-planner | 56ecb18e | 3 | ربط StoryDirector باللقطات والحركة والتكوين والإضاءة |
| 24 | stage-05-storyboard-planner → stage-06-asset-quality-licensing-policy | 54b61c4f | 1 | سياسة جودة وترخيص الأصول؛ وثائق فقط |
| 25 | stage-06-asset-quality-licensing-policy → stages-01-06-audit | 77b01866 | 4 | إصلاح استمرار الحركة عبر حدود الفصول والتحقق من مدة الفيلم |
| 26 | stages-01-06-audit → stage-07-cinematic-asset-inventory | 1335bc62 | 1 | جرد مكتبة الأصول؛ يسجل صفر أصول سينمائية معتمدة في لقطة الجرد |
| 27 | stage-07-cinematic-asset-inventory → stages-01-07-audit | 38069363 | 16 | تقوية الرندر والتوقيت والتنظيف وسلامة الصور واختبارات Android |
| 28 | stage-07-cinematic-asset-inventory → stage-08-asset-registry-quality-gate | 2daeba2e | 25 | Registry وQuality Gate؛ مقارنة PR27→PR28 تظهر 11 ملفًا إضافيًا فقط |
| 29 | stage-08-asset-registry-quality-gate → stage-09-procedural-generation-library | ad9f9095 | 2 | تصميم مكتبة التوليد الإجرائي؛ وثائق فقط |
| 30 | stage-07-cinematic-asset-inventory → stage-10-generator-asset-cooperation | 1ef1b39b | 35 | تعاون المولدات مع الأصول؛ مقارنة PR29→PR30 تظهر 10 ملفات إضافية فقط |
| 31 | stage-07-cinematic-asset-inventory → stage-11-engine-compatibility-poc | 2a95d733 | 73 | اختبارات المحركات واستلام الأصول؛ مقارنة PR30→PR31 تظهر 38 مسارًا إضافيًا |
| 32 | stage-11-engine-compatibility-poc → filament-offline-video-prototype | 6bd005a7 | 13 | نموذج Filament مستقل وتصدير MP4 لخمس ثوانٍ، مع تغييرات إضافية في ملفات التطبيق |
| 33 | filament-offline-video-prototype → stage-12-final-engine-roles | 5556e9c2 | 3 | تثبيت أدوار المحركات؛ تعديل MainActivity تعليق توضيحي فقط |
| 34 | stage-12-final-engine-roles → stage-13-motion-code-library-scene-contract | e65dcdfe | 16 | مكتبة أوامر الحركة وعقد المشهد واختباراتهما وتغييرات CI |

### 4.1 ملاحظات على أعداد الملفات

- PR #3 يعلن 13 ملفًا بسبب قاعدة قديمة. مقارنة رأس PR #2 برأس PR #3 تكشف 9 ملفات إضافية خاصة بالطبقات والعمق.
- PR #28 يعلن 25 ملفًا عند مقارنته بقاعدة Stage 07، لكن المقارنة من رأس PR #27 إلى رأس PR #28 تكشف 11 مسارًا إضافيًا فقط.
- PR #30 يعلن 35 ملفًا بسبب تراكم Stage 08 و09. مقارنة رأس PR #29 برأس PR #30 تكشف 10 ملفات إضافية فقط.
- PR #31 يعلن 73 ملفًا بسبب تراكم Stage 08–10. مقارنة رأس PR #30 برأس PR #31 تكشف 38 مسارًا إضافيًا فقط.
- لا يجوز اعتبار كل الملفات الظاهرة في مقارنة PR تراكمية تغييرات جديدة في ذلك PR.

## 5. خريطة النسب التاريخية وسلاسل التطوير

### 5.1 مسار Phase

العلاقة الفعلية في التاريخ:

main → PR1 → PR2 → PR3 → PR4 → PR5 → (PR6 وثائق فقط كفرع جانبي، PR7) → PR8 → PR9 → PR10 → PR11 → PR12 → PR13

بعدها تظهر مشكلة PR14، ثم:

PR14 → PR15 → PR16 → PR17 → PR18

PR #6 وPR #7 فرعان شقيقان من رأس PR #5؛ PR #6 ليس سلفًا لـPR #7. PR #6 تعديل وثائقي مستقل ويمكن إدخال مرجعه لاحقًا دون تكرار الكود.

### 5.2 مسار Stage

main → PR21 → PR22 → PR23 → PR24 → PR25 → PR26 → PR27

ثم:

PR27 → PR28 → PR29 → PR30 → PR31 → PR32 → PR33 → PR34

لكن قواعد PR الحالية لا تعكس هذه السلسلة بدقة:
- PR #28 رأسه يحتوي PR #27، بينما Base المعلن هو Stage 07.
- PR #30 رأسه يحتوي PR #28 وPR #29، بينما Base المعلن هو Stage 07.
- PR #31 رأسه يحتوي PR #30، بينما Base المعلن هو Stage 07.
- PR #32 وPR #33 وPR #34 لديهم قواعد مباشرة متتابعة وفق رؤوس الآباء.

### 5.3 سلسلة الوثائق

PR19 → PR20

هذه سلسلة وثائق منفصلة عن main حاليًا. تحديث PR #20 لا يعني أن خطة التشغيل أصبحت في main.

## 6. العيوب المؤكدة في قواعد PR

| الطلب | الخلل | الإجراء الصحيح قبل دمج ذلك الطلب |
|---|---|---|
| PR #3 | Base بقي extension-track-r1 رغم أن الرأس يتضمن PR #2 | جعل قاعدة المراجعة PR #2 head أو main بعد دمج PR #2 |
| PR #14 | Base يشير إلى نسخة قديمة من Phase 13 | إدخال أحدث تغييرات PR #13 أولًا، ثم إعادة اختبار PR #14 وجميع نسله |
| PR #28 | Base يشير إلى Stage 07 رغم أن الرأس يحتوي PR #27 | اعتماد PR #27 كسلف مباشر أو دمجه ثم التوجيه إلى main |
| PR #30 | Base يشير إلى Stage 07 رغم أن الرأس يحتوي PR #28 وPR #29 | Base الصحيح التسلسلي هو PR #29 head أو main بعد دمج السلفين |
| PR #31 | Base يشير إلى Stage 07 رغم أن الرأس يحتوي PR #30 | Base الصحيح التسلسلي هو PR #30 head أو main بعد دمجه |
| PR #20 | وثيقة الخطة مستهدفة إلى PR #19 لا main | يبقى هذا مقبولًا كسلسلة وثائق، لكن دمجها في main يحتاج قرار إدخال وثائق مستقل |

تعديل Base لا يحل التعارضات الوظيفية بين المسارين الرئيسيين؛ إنه يعالج عرض التغييرات ونسب PR فقط.

## 7. انحراف Phase 13 / Phase 14

رأس PR #14 هو c7d58027، وقاعدة PR #14 المسجلة هي Commit 0f695bfd.

رأس PR #13 الحالي هو 228cbe90.

مقارنة الرأسين أثبتت:
- أنهما يشتركان في Merge Base هو 0f695bfd.
- رأس PR #13 يحتوي 17 Commit إضافيًا بعد قاعدة PR #14 لا توجد في PR #14.
- الفرعان متباعدان، وليس PR #14 ابنًا لأحدث رأس PR #13.

التغييرات التي يجب إدخالها إلى النسب قبل اعتماد Phase 14 تشمل تعديلات:
- WolfNarrativePatternAudit.kt
- WolfOfflineStoryAiActivity.kt
- WolfStoryOriginalityReviewEngine.kt
- اختبارات الأنماط والأصالة
- README ووثيقة Phase 13.

PR #15 و#16 و#17 و#18 مبنية على رأس PR #14 الحالي؛ لذلك يلزم تحديث سلسلة النسب كاملة بعد إصلاح PR #14، ثم إعادة تشغيل الاختبارات المناسبة على كل رأس جديد. لا يجوز افتراض أن نجاح Phase 14 أو Phase 15 يثبت أن أحدث تغييرات Phase 13 موجودة داخلهما.

## 8. الانفصال الجوهري بين Phase 18 وStage 11

هذه أهم نتيجة في التدقيق.

- رأس Phase 18: 9d5a533e7e7cce6b860bd481a2da3d55da04c301
- رأس Stage 11: 2a95d733541477b806cfb9a9f9083556f0bc1471
- Merge Base: main عند a955907e4870de578b064249ab38e22953e9de52
- حالة المقارنة: Diverged.

أي أن الفرعين انطلقا من main نفسه، ثم تطورا بصورة مستقلة. لا يحتوي أحدهما الآخر.

الملفات المشتركة التي تحتاج إلى توحيد دلالي، لا اختيار نسخة كاملة من أحد الفرعين:

1. README.md
2. app/build.gradle.kts
3. app/src/main/java/com/wolfcodes/offline/MainActivity.kt
4. app/src/main/java/com/wolfcodes/offline/OfflineProductionEngine.kt
5. app/src/main/java/com/wolfcodes/offline/WolfAudioMixer.kt
6. app/src/main/java/com/wolfcodes/offline/WolfImageDepthSegmenter.kt
7. app/src/main/java/com/wolfcodes/offline/WolfProceduralMotionEngine.kt
8. app/src/main/java/com/wolfcodes/offline/WolfSceneArtEngine.kt
9. app/src/main/java/com/wolfcodes/offline/WolfSceneDepthComposer.kt
10. app/src/test/java/com/wolfcodes/offline/OfflineStorageEstimateTest.kt
11. app/src/test/java/com/wolfcodes/offline/WolfAudioMixerTest.kt
12. app/src/test/java/com/wolfcodes/offline/WolfImageDepthSegmenterTest.kt

هذه قائمة الحد الأدنى للملفات المشتركة التي أظهرتها مقارنة GitHub. وقد توجد تبعيات غير مباشرة عبر Manifest وGradle وواجهات مشتركة يجب مراجعتها عند الدمج.

### 8.1 ما الذي يحمله مسار Phase ولا يوجد في مسار Stage؟

- مكتبة الرعب المحلية TXT.
- استوديو كتابة القصص المحلية LiteRT-LM.
- محرك طول القصة والبنية.
- مراجعة الأصالة والمراجع وأنماط السرد.
- منسق إنتاج الأفلام الطويلة وتقسيم المشاهد واللقطات والفصول.
- تسجيل الراوي على أجزاء، واستعادة التسجيلات، وربطها بنسخة القصة المعتمدة.
- هوية WOLF السينمائية، المقدمة، العلامة المائية، ومعالجة الصوت.
- فحص عقد التشغيل Offline.
- تشغيل محاكي Android للتحقق من الإقلاع دون اتصال، وإعدادات تجهيز Release.

هذه ميزات فريدة لا يجوز فقدها لمجرد أن مسار Stage أحدث في ملفات الرندر.

### 8.2 ما الذي يحمله مسار Stage ولا يوجد في مسار Phase؟

- إصلاح مدة الفيلم والتوقيت واختبارات 30 ثانية/7 ثوانٍ.
- StoryDirector وتوجيه اللقطات والتكوين والحركة والإضاءة.
- سياسة جودة وترخيص الأصول.
- جرد مكتبة الأصول.
- Asset Registry وQuality Gate.
- تصميم مكتبة التوليد الإجرائي وتعاون المولدات.
- اختبارات محركات Android المعزولة.
- استلام HDRI كأصول مرشحة.
- نموذج Filament المستقل.
- قرار توزيع أدوار المحركات.
- WolfMotionCodeLibrary وWolfSceneContract.
- تحسين سياسة CI والتوازي والكاش في فرع Stage 13.

هذه الميزات أيضًا يجب الحفاظ عليها وعدم استبدالها تلقائيًا بمكونات Phase.

## 9. نقاط التعارض الوظيفي التي تتطلب قرارًا فنيًا قبل الدمج

### 9.1 معالجة صور المستخدم وفشل التقسيم

- PR #1 أضاف attemptSegmentation لاحتواء استثناءات التقسيم وإصدار تحذير بدل إيقاف الفيلم.
- PR #4 و#5 أضافا UploadedImageEngine وSceneRenderPipeline.
- PR #7 حسّن خوارزمية فصل حدود الصورة والمكونات المتصلة؛ لكنه يظل تحليلًا إرشاديًا heuristic وليس تقسيمًا دلاليًا عامًا.
- Stage #22 قرر أن الصورة التي اختارها المستخدم إذا تعذر فك ترميزها لا تستبدل برسوم؛ يجب إظهار خطأ واضح.
- Stage #22 و#27 قررا المحافظة على الصورة الأصلية عند فشل التقسيم، وعدم تطبيق حركة كاميرا قد تشوهها.
- Stage #27 أضاف semanticValidationPassed وsafeForParallax، وتُرجع isSemanticSegmentationReady() القيمة false في رأس الفرع الذي راجعناه. وبذلك يظل Parallax/Camera Warp معطلًا حتى وجود تحقق دلالي حقيقي.

**قاعدة الدمج المقترحة:** الحفاظ على وحدات Phase الفريدة، لكن يجب أن يحكم Stage safe-fallback اختيار مسار صورة المستخدم. لا تُفعّل حركة 2.5D على أساس confidence heuristic وحده. وتظل صورة المستخدم الأصلية هي المصدر، ولا تُستبدل صورة اختارها المستخدم بصورة إجرائية عند فشل فك الترميز.

مسار المشاهد بلا صور يبقى مسارًا مستقلًا، ولا يختلط بمسار صورة مستخدم فشل تحميلها.

### 9.2 مدة الفيلم ومدة الراوي

- Stage #21 يثبت مدة المشروع المحددة كمرجع للفيديو، ويمنع أن يقصر تعليق مدته 7 ثوانٍ فيلمًا مطلوبًا 30 ثانية.
- PR #25 يستخرج سياسة AudioTimeline ويضيف اختبار 30 ثانية مع صوت 7 ثوانٍ.
- PR #32 يعدل buildAudioTimeline ليصبح طول تسجيل الراوي المعتمد هو مرجع مدة الفيلم عندما يكون narrationTimingEnabled، وتُمدد اللقطات والإطارات حتى تغطي التسجيل كاملًا.
- Stage #33 يوثق القرار صراحة: مدة تسجيل الراوي المعتمد هي مدة الفيلم المرجعية، ويقوم WOLF بتمديد المشاهد والإطارات بدل قص التسجيل.

**التعارض:** هذا القرار الأخير يناقض نصوصًا أقدم في الخطة كانت تجعل مدة المشروع المختارة هي المرجع، وتطلب قرارًا من المستخدم عندما يكون الصوت أطول.

**قاعدة الدمج المقترحة:** لا يُختار أحد السلوكين بصمت. يجب اعتماد قاعدة منتج موحدة في الخطة الرئيسية، ثم مطابقة التنفيذ واختبارات القبول معها قبل الدمج. يُحافظ على ضمان عدم قص الراوي وعلى فصل مساري الصوت والفيديو، لكن مصدر المدة المرجعي يجب أن يكون قرارًا واحدًا موثقًا.

### 9.3 Filament والتغيير داخل مسار الإنتاج

وصف PR #32 يقول إن تجربة Filament مستقلة ولا تعدل OfflineProductionEngine. لكن قائمة الملفات الفعلية تتضمن OfflineProductionEngine.kt، والـpatch يغير buildAudioTimeline هناك.

كما يضيف PR #32 اعتمادات Filament مباشرة في app/build.gradle.kts، وليس فقط ضمن وحدة أو إعداد Debug مستقل. وجود زر اختبار Debug-only لا يعني تلقائيًا استبعاد المكتبات الأصلية والاعتمادات من Release APK.

**المطلوب قبل الدمج:**
- التعامل مع تعديل AudioTimeline على أنه تغيير إنتاجي حقيقي، وليس مجرد POC.
- عزل اعتماديات تجربة Filament أو إثبات الحاجة إلى تضمينها في نسخة الإنتاج وقياس أثرها على APK وABI.
- الحفاظ على OfflineProductionEngine الحالي؛ لا يحل HorrorVideoExporter محل خط الإنتاج الرئيسي بمجرد نجاح تصدير مدته خمس ثوانٍ.
- اختبار مدة الفيديو والصوت والفصول والاستكمال والتصدير الطويل بعد توحيد القرار الزمني.

### 9.4 المولدات والأصول

- Stage 07 يسجل صفر أصول سينمائية APPROVED في لقطة الجرد.
- Stage 08 ينشئ Registry وQuality Gate، لكن manifest الإنتاج فارغ عمدًا.
- Stage 09 تصميم فقط.
- Stage 10 يقدم ثلاثة مولدات بيئية، لكن حدود الموارد الإنتاجية في حالة PENDING/صفر؛ لذلك تفشل استدعاءات الإنتاج مغلقة حتى اعتماد قياسات فعلية. حدود الاختبارات synthetic ومخصصة للاختبارات، وليست قياسات جهاز.
- Stage 11 أضاف HDRIs مرشحة Candidate، ولم تصبح أصولًا إنتاجية معتمدة ولم تدخل APK أو Registry.

**قاعدة الدمج المقترحة:** لا إعلان باكتمال مكتبة الأصول أو جاهزية المولدات للإنتاج قبل 100% تغطية المتطلبات الإلزامية، والحقوق، والتبعيات، والقياسات، والاختبارات الفعلية.

## 10. تدقيق أدلة الاختبار على Commits محددة

نجاح اختبار على Commit قديم لا يثبت نجاح Head أحدث. الجدول التالي يربط الأدلة بالـSHA الذي اختُبر.

| النطاق | Commit | دليل Actions | النتيجة وحدودها |
|---|---|---|---|
| Phase 13 | 228cbe90 | 44777037297 | نجح Offline quality checks: Lint وJVM؛ لا يثبت APK أو Android Instrumentation |
| Phase 14 | c7d58027 | 44766381716 | Lint وJVM ناجحان؛ لا محاكي أو جهاز |
| Phase 18 | 9d5a533e | 44825931432 | Offline runtime guard وLint وJVM ناجحة؛ لا Release APK موقّع ولا اختبار Realme 7 |
| Stage 03 | 17a60591 | 44900044163 | Offline quality workflow فقط؛ لا يثبت بناء APK أو Instrumentation |
| Stage 07 Audit PR27 | 38069363 | 44938194797 | Debug APK وLint وJVM ناجحة؛ اختبار Android 12 ناجح؛ سجل PR يذكر 7/7 بلا تخطي أو فشل |
| Stage 08 PR28 | 2daeba2e | 44955139178 | Debug APK وLint وJVM؛ Android 12 instrumentation ناجح؛ سجل PR يذكر 12/12 |
| Stage 10 PR30 | 1ef1b39b | 44967071317 | build/lint/JVM وAndroid 12 regression ناجحة على الرأس المشار إليه |
| Stage 11 PR31 | 2a95d733 | 45071779727 و45071779717 و45071779726 | engine POC وKorGE packaging وStage01-08 regression وasset intake ناجحة على هذا الرأس |
| Filament PR32 | 6bd005a7 | 45152511800 | Debug APK وLint وJVM وARM64 packaging وAndroid12 5-second Filament MP4 test ناجحة |
| Stage 13 PR34 | e65dcdfe | لا توجد Run/Status مرتبطة بالرأس الحالي في استعلام التدقيق | لا يثبت نجاح الرأس الحالي |
| Stage 13 تشغيل سابق | e4b52555 | 45190168737 | 4 وظائف ناجحة وواحدة فاشلة؛ ليس رأس PR34 الحالي |

### 10.1 فشل Godot في تشغيل Stage 13 السابق

التشغيل 45190168737 كان على Commit e4b52555 وليس e65dcdfe.

النتائج:
- فحوص الأصول نجحت.
- بناء Debug APK وLint واختبارات JVM نجحت.
- تغليف KorGE نجح.
- Android 12 app integration regression نجحت.
- وظيفة POC للمحركات فشلت في connectedGodotDebugAndroidTest.

السجل يبين أن بناء وتغليف اختبارات Godot نجحا، وبدأت جولتا اختبار على المحاكي، ثم انتهت رسالة:
Instrumentation run failed due to Process crashed.

لم يظهر في السجل المتاح FATAL EXCEPTION أو Caused by أو SIGSEGV أو SIGABRT. كما ظهر تحذير فشل بدء Emulator Console. لذلك لا يثبت السجل أن السبب هو عيب في كود Godot أو التطبيق أو المحاكي؛ السبب الأصلي ما زال غير محسوم.

كذلك سجل Gradle cache في هذا التشغيل أظهر 0 restored و0 saved، وكان Read-only. لا يجوز وصف ذلك التشغيل بأنه أثبت أن الكاش يعمل.

### 10.2 نتائج POC لا تعني دمجًا إنتاجيًا

- Godot 4.7.2: اجتاز POC معزولًا على رأس PR31 السابق، لكنه لم يثبت على رأس PR34 بعد آخر تغييرات Workflow.
- Filament 1.75.0: اجتاز اختبار POC وتصدير خمس ثوانٍ على رأس PR32؛ ليس بديلًا مثبتًا لخط MP4 الكامل.
- libGDX وLottie: اجتازا اختبارات POC المعزولة على رأس PR31.
- KorGE: نجح بناء APK/AAB في POC مع JDK 21 المعزول.
- SceneView: حل الاعتماد؛ هو واجهة فوق Filament وليس محركًا مستقلًا.
- Cocos Creator 3.8.7: BLOCKED — NOT PASSED؛ لا يوجد مشروع Android أصلي صادر من Creator مع إثبات إطار مرسوم. يبقى خارج APK الإنتاجي.

## 11. تصنيف التغييرات الفريدة والموروثة

### 11.1 تغييرات موروثة أو مكررة في نطاق PR

- PR #3 يرث تغييرات PR #2؛ لذلك لا يُدمج PR #3 كطلب مستقل فوق قاعدة d99edcd القديمة.
- PR #28 يتضمن تغييرات PR #27 بسبب قاعدة قديمة؛ المقارنة الصحيحة بين الرأسين تكشف نطاق Registry الإضافي فقط.
- PR #30 يتضمن Stage08 وStage09 في الـdiff المعلن؛ التغيير الفريد هو نطاق تعاون المولدات.
- PR #31 يتضمن Stage08–10 في الـdiff المعلن؛ التغيير الفريد هو نطاق POCs واستلام الأصول.
- PR #32–34 سلسلة صحيحة في النسب، لكن يجب مراجعة التغيير الإنتاجي في OfflineProductionEngine والاعتماديات في app/build.gradle.kts.
- الفروع support/baseline المذكورة في القسم 3 لا تحمل ميزات مستقلة إضافية غير PR1 أو main.

### 11.2 تغييرات فريدة يجب الحفاظ عليها

| المصدر | المحتوى الفريد الذي يجب أن يبقى |
|---|---|
| Phase #10–13 | مكتبة القصص، Story AI، بنية الطول، الأصالة ومراجعة السرد |
| Phase #14–15 | تنسيق الفيلم الطويل، الفصل إلى لقطات، نقاط الاستكمال، تسجيل الراوي على أجزاء، الصوت والهوية |
| Phase #16–18 | Offline runtime guard، محاكي الإقلاع دون اتصال، بوابة Release |
| Stage #21–23 | توقيت الفيلم، سياسة fallback، StoryDirector وخطط اللقطات |
| Stage #24–27 | حقوق الأصول، الجرد، Registry readiness safety، render/image/timing regression |
| Stage #28–30 | Asset Registry، تصميم ومكتبة مولدات البيئة |
| Stage #31 | POCs، intake tools، Candidate assets، Cocos blocker |
| Stage #32–33 | Filament POC وأدوار المحركات |
| Stage #34 | Motion Code Library وScene Contract وCI queue policy |

هذه قائمة نطاقات فريدة بحسب PR descriptions وchanged-file inventories؛ لا تعني أن كل خاصية اجتازت اختبار الجهاز أو أنها مكتملة إنتاجيًا.

## 12. خطة الاستعادة الآمنة المقترحة

هذه خطة ترتيب، وليست تنفيذًا أو موافقة على الدمج.

### المرحلة A — تثبيت أساس الإنتاج

1. إبقاء main كما هو وعدم إعادة كتابة تاريخه.
2. استخدام سلسلة Stage #21–#27 كمرجع أولي لتغييرات المدة والرندر وسلامة الصور؛ PR #27 لديه دليل Android 12 regression على رأسه.
3. مراجعة كل PR من #21 إلى #27 على قاعدة مباشرة، ثم عرضه للدمج إلى main واحدًا تلو الآخر بعد نجاح فحوص الرأس الجديد.
4. بعد كل دمج، تشغيل Post-Merge على main الجديد قبل الانتقال.

### المرحلة B — إدخال Registry والمحركات

1. تصحيح PR #28 ليكون مبنيًا على PR #27، أو على main بعد دمجه.
2. PR #29 بعد PR #28.
3. PR #30 بعد PR #29.
4. PR #31 بعد PR #30.
5. PR #32 بعد PR #31 بعد معالجة التغيير الإنتاجي في OfflineProductionEngine وعزل Filament dependencies أو اعتمادها.
6. PR #33 بعد PR #32.
7. PR #34 بعد PR #33، لكن لا يُغلق Stage13 حتى يُشغّل CI على رأسه الفعلي وتُحسم مشكلة Godot instrumentation أو تُحدد حدود اختبار POC بصورة دقيقة.

### المرحلة C — إدخال ميزات Phase دون خسارة خصائص Stage

1. لا تُدمج Phase18 ككتلة واحدة فوق Stage line؛ المساران متباعدان ويحتويان تغييرات متعارضة.
2. تؤخذ مكونات Phase الفريدة بحسب نطاقها وتُدمج تسلسليًا بعد مراجعة أثرها مع أساس Stage المستقر.
3. Phase #2–5 الخاصة بالمشاهد الإجرائية وصور المستخدم تحتاج مواءمة مع Stage Scene/StoryDirector pipeline وقاعدة عدم استبدال صورة المستخدم.
4. Phase #7–9 الخاصة بالتقسيم والحركة 2.5D لا تُفعّل حركتها إلا عند توفر semantic segmentation موثوق أو قرار منتج جديد؛ لا يُكسر قفل Stage27.
5. Phase #10–13 يمكن إدخالها كمكونات قصة/مكتبة/مراجعة مستقلة بعد مراجعة عقود البيانات والتكامل مع الواجهة.
6. قبل Phase14، تُوحّد قاعدة PR #13 مع أحدث رأس 228cbe90، ثم يعاد تدقيق PR14 واختبار PR14–18 على قواعد محدثة.

### المرحلة D — إغلاق النظام

- اختبار regression الكامل على أحدث main بعد كل دمج.
- إكمال POCs/Adapters حسب أدوار المحركات.
- يبقى Cocos محجوبًا حتى تقديم المشروع الأصلي والإثبات.
- اختبار Realme 7 الفعلي يبقى بوابة مستقلة كما هو في الخطة.
- تجهيز Release APK والتوقيع لا يبدأان قبل بواباتهما وموافقة المستخدم.

## 13. ما يجب ألا يحدث أثناء الاستعادة

- لا دمج لجميع PRs دفعة واحدة.
- لا دمج PR #3 فوق Base قديم مع PR #2 ثم قبول كل ملف مكرر بلا مقارنة.
- لا دمج PR #14 دون أحدث Phase13 ancestry.
- لا دمج PR #28/#30/#31 إلى Stage07 القديمة كما لو كانت تغييرات مستقلة؛ هذه PRs تراكمية.
- لا اختيار نسخة كاملة من OfflineProductionEngine من مسار Phase أو Stage على حساب الآخر.
- لا استبدال صورة اختارها المستخدم بمشهد مولد إذا تعذر فكها أو تحميلها.
- لا تفعيل حركة كاميرا/Parallax من segmentation heuristic غير دلالي.
- لا اعتماد Filament في Release اعتمادًا على أن زر الاختبار مخفي في Debug.
- لا اعتبار POC محركًا معتمدًا إنتاجيًا أو اعتبار تغليف APK دليل جودة سينمائية.
- لا اعتبار CI Run أخضر على SHA قديم نجاحًا لرأس أحدث.
- لا حذف أو إعادة تسمية baseline/support branches قبل اكتمال استعادة التغييرات وتوثيق مصيرها.

## 14. قرار الخطوة التالية المقترح

الخطوة التالية بعد هذا التقرير يجب أن تكون **مراجعة تعارضات الكود سطرًا بسطر في الملفات المشتركة الاثني عشر**، بدءًا من:

1. OfflineProductionEngine.kt
2. WolfImageDepthSegmenter.kt
3. MainActivity.kt
4. app/build.gradle.kts
5. WolfProceduralMotionEngine.kt
6. WolfSceneArtEngine.kt
7. WolfSceneDepthComposer.kt
8. WolfAudioMixer.kt
9. اختبارات الملفات المقابلة
10. README وملفات Workflows ذات الصلة.

مخرجات الخطوة التالية ستكون ملف قرار لكل ملف: سلوك main الحالي، تغييرات Phase، تغييرات Stage، التعارض، الوظائف التي يجب الاحتفاظ بها، الخطة النهائية للمحتوى، والاختبارات المطلوبة.

لا يُنشأ recovery branch ولا تُعدّل قواعد PR ولا يُنفذ Merge حتى تُعتمد خريطة هذه التعارضات ويصدر توجيه المستخدم للخطوة التالية.

## 15. سجل السلامة والتنفيذ لهذه الخطوة

- تمت مراجعة قائمة الفروع وPRs والـBase والـHead والـCommit SHA.
- تمت مراجعة قوائم الملفات المتغيرة لجميع PRs من #1 إلى #34.
- تمت مقارنة علاقات الأسلاف ومناطق التباعد الرئيسية.
- تمت مراجعة التغييرات التفصيلية للملفات الأساسية التي يظهر عليها التعارض.
- تمت مطابقة نتائج CI مع SHA محدد وعدم نقل النتائج بين الرؤوس.
- لم يتم تعديل كود التطبيق أو Gradle أو Workflows.
- لم يتم إنشاء أو حذف أو إعادة تسمية أي فرع.
- لم يتغير Base أو Head لأي PR.
- لم يتم إغلاق أو دمج أي PR.
- لم تتم إعادة كتابة تاريخ Git.
- هذا التقرير توثيقي فقط، والخطوة التالية تحتاج توجيه المستخدم.

**انتهى التدقيق البنيوي الأولي. لا يعتبر المستودع جاهزًا للدمج قبل مراجعة التعارضات الفعلية والاختبارات على Commit المرشح لكل دمج.**
