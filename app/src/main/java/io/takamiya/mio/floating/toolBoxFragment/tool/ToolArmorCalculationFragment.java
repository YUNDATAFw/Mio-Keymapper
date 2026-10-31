package io.takamiya.mio.floating.toolBoxFragment.tool;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import java.util.Map;

import io.takamiya.mio.R;
import io.takamiya.mio.databinding.FragmentToolArmorCalculationBinding;
import io.takamiya.mio.floating.toolBoxFragment.ToolboxBaseFragment;
import io.takamiya.mio.floating.toolBoxFragment.tool.armor.DamageReductionSimulator;

public class ToolArmorCalculationFragment extends ToolboxBaseFragment {

    private FragmentToolArmorCalculationBinding binding;

    private final String[] ARMOR_MATERIALS = {"无", "皮革", "铜", "金", "锁链", "铁", "钻石", "下界合金", "海龟壳"};
    private final String[] DAMAGE_TYPES = {
        "普通伤害", "饥饿/虚空/kill", "监守者远程攻击",
        "窒息/溺水/魔法/动能/冰冻", "着火", "熔岩/岩浆块/小火球",
        "爆炸", "弹射物", "摔落"
    };
    private final DamageReductionSimulator.DamageType[] DAMAGE_TYPE_ENUMS = {
        DamageReductionSimulator.DamageType.ALL_EXCEPT_LISTED,
        DamageReductionSimulator.DamageType.HUNGER_VOID_KILL,
        DamageReductionSimulator.DamageType.WARDEN_RANGED,
        DamageReductionSimulator.DamageType.SUFFOCATE_DROWN_MAGIC,
        DamageReductionSimulator.DamageType.ON_FIRE,
        DamageReductionSimulator.DamageType.LAVA_MAGMA_FIREBALL,
        DamageReductionSimulator.DamageType.EXPLOSION,
        DamageReductionSimulator.DamageType.PROJECTILE,
        DamageReductionSimulator.DamageType.FALL
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container) {
        binding = FragmentToolArmorCalculationBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(View view) {
        ArrayAdapter<String> materialAdapter = new ArrayAdapter<>(
                getContext(), android.R.layout.simple_dropdown_item_1line, ARMOR_MATERIALS);
        binding.spinnerHelmet.setAdapter(materialAdapter);
        binding.spinnerChestplate.setAdapter(materialAdapter);
        binding.spinnerLeggings.setAdapter(materialAdapter);
        binding.spinnerBoots.setAdapter(materialAdapter);

        ArrayAdapter<String> damageTypeAdapter = new ArrayAdapter<>(
                getContext(), android.R.layout.simple_dropdown_item_1line, DAMAGE_TYPES);
        binding.spinnerDamageType.setAdapter(damageTypeAdapter);
        binding.spinnerDamageType.setText(DAMAGE_TYPES[0], false);

        binding.etDamage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                String text = s.toString().trim();
                if (!text.isEmpty()) {
                    try {
                        double val = Double.parseDouble(text);
                        binding.tvRawDamage.setText(String.format("%.2f", val));
                    } catch (NumberFormatException e) {
                        binding.tvRawDamage.setText("0");
                    }
                } else {
                    binding.tvRawDamage.setText("0");
                }
            }
        });

        binding.sliderResistance.addOnChangeListener((s, v, fromUser) -> {});
        binding.sliderBreach.addOnChangeListener((s, v, fromUser) -> {});

        binding.btnCalculate.setOnClickListener(v -> calculate());
    }

    private void calculate() {
        String damageStr = binding.etDamage.getText().toString().trim();
        if (damageStr.isEmpty()) {
            showToast("请输入伤害值");
            return;
        }

        double rawDamage;
        try {
            rawDamage = Double.parseDouble(damageStr);
        } catch (NumberFormatException e) {
            showToast("请输入有效的数字");
            return;
        }
        if (rawDamage < 0) {
            showToast("伤害值不能为负数");
            return;
        }

        String helmetName = binding.spinnerHelmet.getText().toString();
        String chestplateName = binding.spinnerChestplate.getText().toString();
        String leggingsName = binding.spinnerLeggings.getText().toString();
        String bootsName = binding.spinnerBoots.getText().toString();

        DamageReductionSimulator.ArmorMaterial helmet = DamageReductionSimulator.ArmorMaterial.byName(helmetName);
        DamageReductionSimulator.ArmorMaterial chestplate = DamageReductionSimulator.ArmorMaterial.byName(chestplateName);
        DamageReductionSimulator.ArmorMaterial leggings = DamageReductionSimulator.ArmorMaterial.byName(leggingsName);
        DamageReductionSimulator.ArmorMaterial boots = DamageReductionSimulator.ArmorMaterial.byName(bootsName);

        int totalArmor = helmet.armorSum() + chestplate.armorSum() + leggings.armorSum() + boots.armorSum();
        double totalToughness = helmet.toughnessSum() + chestplate.toughnessSum() + leggings.toughnessSum() + boots.toughnessSum();

        int breach = (int) binding.sliderBreach.getValue();
        int resistanceLevel = (int) binding.sliderResistance.getValue();

        String damageTypeName = binding.spinnerDamageType.getText().toString();
        DamageReductionSimulator.DamageType damageType = DamageReductionSimulator.DamageType.ALL_EXCEPT_LISTED;
        for (int i = 0; i < DAMAGE_TYPES.length; i++) {
            if (DAMAGE_TYPES[i].equals(damageTypeName)) {
                damageType = DAMAGE_TYPE_ENUMS[i];
                break;
            }
        }

        DamageReductionSimulator.Result result = DamageReductionSimulator.calculate(
                rawDamage, damageType, totalArmor, totalToughness, breach,
                (Map<DamageReductionSimulator.EnchantmentType, Integer>) null, resistanceLevel);

        binding.tvFinalDamage.setText(String.format("%.2f", result.finalDamage()));

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("护甲值: %d  韧性: %.0f  破甲: %d\n", result.armor(), result.toughness(), result.breach()));
        sb.append(String.format("护甲减免: %.2f%% → %.2f\n\n", result.armorReductionPct(), result.damageAfterArmor()));
        sb.append(String.format("抗性等级: %d\n", result.resistanceLevel()));
        sb.append(String.format("抗性减免: %.2f%% → %.2f\n\n", result.resistanceReductionPct(), result.damageAfterResistance()));
        sb.append(String.format("最终伤害: %.2f\n", result.finalDamage()));
        sb.append(String.format("总减免: %.2f%%", result.totalReductionPct()));

        binding.tvResult.setText(sb.toString());
        binding.cardResult.setVisibility(View.VISIBLE);
    }
}
